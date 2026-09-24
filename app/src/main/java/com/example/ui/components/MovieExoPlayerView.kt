package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.widget.Toast
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.network.SmartNetworkBoosterEngine
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SpaceBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun MovieExoPlayerView(isMiniPlayer: Boolean = false, onMiniPlayerToggle: () -> Unit = {}, 
    streamUrl: String,
    modifier: Modifier = Modifier,
    channelName: String = "Movie Stream",
    isFullScreen: Boolean = false,
    isInPipMode: Boolean = false,
    customHeaders: Map<String, String> = emptyMap(),
    subtitles: List<com.example.scraper.SubtitleTrack> = emptyList(),
    onFullScreenToggle: () -> Unit = {},
    onPlaybackError: (String) -> Unit = {},
    onBack: () -> Unit = {},
    isSeries: Boolean = false,
    onNextEpisode: (() -> Unit)? = null,
    initialStartPositionMs: Long = 0L,
    onProgressUpdate: (Long, Long) -> Unit = { _, _ -> },
    isAnime: Boolean = false,
    subServers: List<com.example.scraper.AnikotoServer> = emptyList(),
    dubServers: List<com.example.scraper.AnikotoServer> = emptyList(),
    selectedAnikotoServer: com.example.scraper.AnikotoServer? = null,
    onSelectAnikotoServer: ((com.example.scraper.AnikotoServer) -> Unit)? = null,
    embedServers: List<Pair<String, String>> = emptyList(),
    currentEmbedServerIndex: Int = 0,
    onSelectEmbedServerIndex: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var currentUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Subtitles State
    var isSubtitlesEnabled by remember { mutableStateOf(true) }
    var selectedSubtitleLang by remember { mutableStateOf<String?>("en") }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var subtitleHudMessage by remember { mutableStateOf<String?>(null) }

    // Playback Speed State
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Position and Timeline State
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    // Settings & Qualities State
    var selectedQuality by remember { mutableStateOf("Auto") }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var settingsMenuPage by remember { mutableStateOf("MAIN") }

    // Touch & Drag Gesture States
    var isFastForward2x by remember { mutableStateOf(false) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var tempSeekPosition by remember { mutableLongStateOf(0L) }
    var dragSeekOffset by remember { mutableLongStateOf(0L) }

    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    var currentBrightness by remember {
        mutableStateOf(
            activity?.window?.attributes?.screenBrightness?.let { if (it < 0) 0.5f else it } ?: 0.5f
        )
    }

    var initialVolume by remember { mutableIntStateOf(0) }
    var initialBrightness by remember { mutableFloatStateOf(0.5f) }
    var initialPositionForSeek by remember { mutableLongStateOf(0L) }

    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapX by remember { mutableFloatStateOf(0f) }
    var showLeftDoubleTapAnim by remember { mutableStateOf(false) }
    var showRightDoubleTapAnim by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showDownloaderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showLeftDoubleTapAnim) {
        if (showLeftDoubleTapAnim) {
            delay(650)
            showLeftDoubleTapAnim = false
        }
    }

    LaunchedEffect(showRightDoubleTapAnim) {
        if (showRightDoubleTapAnim) {
            delay(650)
            showRightDoubleTapAnim = false
        }
    }

    LaunchedEffect(subtitleHudMessage) {
        if (subtitleHudMessage != null) {
            delay(2000)
            subtitleHudMessage = null
        }
    }

    // Build Native Track Selector
    val trackSelector = remember { DefaultTrackSelector(context) }

    // Apply native quality and subtitle restrictions
    LaunchedEffect(selectedQuality, isSubtitlesEnabled, selectedSubtitleLang) {
        val params = trackSelector.parameters.buildUpon()
        when (selectedQuality) {
            "1080p" -> params.setMaxVideoSize(1920, 1080)
            "720p" -> params.setMaxVideoSize(1280, 720)
            "480p" -> params.setMaxVideoSize(854, 480)
            else -> params.setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
        }
        if (!isSubtitlesEnabled) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            if (!selectedSubtitleLang.isNullOrBlank()) {
                params.setPreferredTextLanguage(selectedSubtitleLang)
            }
        }
        trackSelector.parameters = params.build()
    }

    // Initialize and maintain ExoPlayer
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isRetryingWithoutSidecarSubtitles by remember(currentUrl) { mutableStateOf(false) }
    var hasRenderedVideoFrame by remember(currentUrl) { mutableStateOf(false) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUrl, isRetryingWithoutSidecarSubtitles) {
        if (currentUrl.isBlank()) return@LaunchedEffect
        if (currentUrl == lastLoadedUrl && exoPlayer != null && !isRetryingWithoutSidecarSubtitles) {
            return@LaunchedEffect
        }
        lastLoadedUrl = currentUrl
        errorMessage = null
        hasRenderedVideoFrame = false
        exoPlayer?.release()

        val sharedPrefs = context.getSharedPreferences("stream_app_prefs", android.content.Context.MODE_PRIVATE)
        val userBufferIndex = sharedPrefs.getInt("setting_buffer_index", 1)
        val userDecoderIndex = sharedPrefs.getInt("setting_decoder_index", 0)
        val isHwAccel = sharedPrefs.getBoolean("setting_hardware_accel", true)

        val httpDataSourceFactory = SmartNetworkBoosterEngine.createBoostedHttpDataSourceFactory(
            customHeaders = customHeaders,
            url = currentUrl
        )
        val mediaSourceFactory = SmartNetworkBoosterEngine.createOptimizedMediaSourceFactory(context, httpDataSourceFactory)
        val loadControl = SmartNetworkBoosterEngine.createDynamicLoadControl(userBufferIndex, isLiveStream = false)
        val renderersFactory = SmartNetworkBoosterEngine.createRenderersFactory(
            context = context,
            decoderMode = userDecoderIndex,
            isHardwareAccelerated = isHwAccel
        )

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                val mediaItemBuilder = MediaItem.Builder().setUri(currentUrl)
                val urlLower = currentUrl.lowercase()
                if (urlLower.contains(".m3u8") || urlLower.contains("m3u8") || urlLower.contains("hls")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                } else if (urlLower.contains(".mpd") || urlLower.contains("dash")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                } else {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MP4)
                }

                if (subtitles.isNotEmpty() && !isRetryingWithoutSidecarSubtitles) {
                    val subtitleConfigs = subtitles.mapNotNull { sub ->
                        if (sub.url.isBlank()) return@mapNotNull null
                        val mimeType = if (sub.url.lowercase().contains(".vtt") || sub.url.lowercase().contains("vtt")) {
                            androidx.media3.common.MimeTypes.TEXT_VTT
                        } else if (sub.url.lowercase().contains(".srt") || sub.url.lowercase().contains("srt")) {
                            androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        } else if (sub.url.lowercase().contains(".ass") || sub.url.lowercase().contains(".ssa")) {
                            androidx.media3.common.MimeTypes.TEXT_SSA
                        } else {
                            androidx.media3.common.MimeTypes.TEXT_VTT
                        }
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                            .setMimeType(mimeType)
                            .setLanguage(sub.lang.ifBlank { "en" })
                            .setLabel(sub.label.ifBlank { "English" })
                            .setSelectionFlags(if (sub.default) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    }
                    if (subtitleConfigs.isNotEmpty()) {
                        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
                    }
                }

                setMediaItem(mediaItemBuilder.build())
                prepare()
                if (initialStartPositionMs > 0L) {
                    seekTo(initialStartPositionMs)
                }
                setPlaybackSpeed(playbackSpeed)
                playWhenReady = true
                play()
            }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                isPlaying = isPlayingParam
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = player.isPlaying || playWhenReady
                if (playWhenReady && !player.isPlaying) {
                    player.play()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    errorMessage = null
                    player.playWhenReady = true
                    player.play()
                } else if (state == Player.STATE_BUFFERING) {
                    errorMessage = null
                } else if (state == Player.STATE_ENDED) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    // Only treat as an error card if total duration is definitively under 30 seconds
                    if (dur in 1L..30_000L && pos < dur) {
                        android.util.Log.w("MovieExoPlayerView", "Stream ended prematurely at ${pos}ms (duration: ${dur}ms). Rejecting error stream...")
                        onPlaybackError("Stream ended prematurely (error card detected).")
                    } else if (pos < 30_000L && dur > 60_000L) {
                        // Real episode/movie reached segment boundary or transient buffer end; resume smoothly
                        android.util.Log.w("MovieExoPlayerView", "Stream ended early during initial segment at ${pos}ms (duration: ${dur}ms). Retrying playback at position...")
                        player.seekTo(pos)
                        player.prepare()
                        player.playWhenReady = true
                        player.play()
                    } else if (isSeries) {
                        onNextEpisode?.invoke()
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedVideoFrame = true
                isPlaying = true
                player.playWhenReady = true
                player.play()
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("MovieExoPlayerView", "Playback exception occurred: ${error.message}", error)
                if (subtitles.isNotEmpty() && !isRetryingWithoutSidecarSubtitles) {
                    android.util.Log.w("MovieExoPlayerView", "Retrying video playback without sidecar subtitles to bypass 404 subtitle error...")
                    isRetryingWithoutSidecarSubtitles = true
                    return
                }
                onPlaybackError(error.message ?: "Playback error encountered.")
            }
        })

        exoPlayer = player
    }

    // Safe Auto-play kicker
    LaunchedEffect(exoPlayer, currentUrl) {
        val p = exoPlayer ?: return@LaunchedEffect
        p.playWhenReady = true
        p.play()

        // Periodically verify playback status while player is active
        while (true) {
            delay(2000)
            val currentP = exoPlayer ?: break
            if (currentP.playbackState == Player.STATE_READY) {
                if (!currentP.isPlaying || !currentP.playWhenReady) {
                    currentP.playWhenReady = true
                    currentP.play()
                }
            }
        }
    }

    // Timeline tracker loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            val player = exoPlayer
            if (player != null && !isDraggingSlider) {
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                if (currentPosition > 0L && duration > 0L) {
                    onProgressUpdate(currentPosition, duration)
                }
            }
            delay(1000)
        }
    }

    // Auto-hide controls after 4 seconds of active playback
    LaunchedEffect(showControls, isPlaying, isScreenLocked) {
        if (showControls && isPlaying && !isScreenLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Manage Lifecycle events
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!isInPipMode) {
                        exoPlayer?.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer?.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Maintain screen flags while playing
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val currentSpeedState by rememberUpdatedState(playbackSpeed)
    val currentPositionState by rememberUpdatedState(currentPosition)
    val durationState by rememberUpdatedState(duration)
    val isScreenLockedState by rememberUpdatedState(isScreenLocked)
    val exoPlayerState by rememberUpdatedState(exoPlayer)

    // Gesture pointer logic block
    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            if (isScreenLockedState) {
                // If screen is locked, any tap just toggles controls/lock button visibility, other gestures are ignored
                while (true) {
                    val event = awaitPointerEvent()
                    val anyActive = event.changes.any { it.pressed }
                    if (!anyActive) {
                        showControls = !showControls
                        break
                    }
                }
                return@awaitEachGesture
            }

            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val startTime = System.currentTimeMillis()
            val startX = down.position.x
            val startY = down.position.y
            val screenWidth = size.width
            val screenHeight = size.height

            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            initialBrightness = activity?.window?.attributes?.screenBrightness?.let { if (it < 0) 0.5f else it } ?: 0.5f
            initialPositionForSeek = currentPositionState

            var dragDirection: String? = null
            var hasMoved = false
            var isLongPressActive = false
            var wasLongPressActive = false
            var isPointerActive = true
            var dragSeekPosition = currentPositionState

            // Launch timer for Touch & Hold 2X Fast Forward (fires after 280ms if held)
            val holdJob = scope.launch {
                delay(280)
                if (isPointerActive && !hasMoved && !isScreenLockedState) {
                    isLongPressActive = true
                    wasLongPressActive = true
                    isFastForward2x = true
                    exoPlayerState?.setPlaybackSpeed(2.0f)
                }
            }

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                    if (change == null || !change.pressed) break

                    val dx = change.position.x - startX
                    val dy = change.position.y - startY

                    if (isLongPressActive) {
                        // While 2X speed is active, do not cancel or switch to seek/brightness/volume on minor jitter
                        if (abs(dx) > 100f || abs(dy) > 100f) {
                            isLongPressActive = false
                            isFastForward2x = false
                            exoPlayerState?.setPlaybackSpeed(currentSpeedState)
                            hasMoved = true
                        }
                    } else if (!hasMoved) {
                        if (abs(dx) > 28f || abs(dy) > 28f) {
                            hasMoved = true
                            holdJob.cancel()
                        }
                    }

                    if (hasMoved && !isLongPressActive) {
                        if (dragDirection == null) {
                            if (abs(dx) > abs(dy)) {
                                dragDirection = "seek"
                            } else {
                                dragDirection = if (startX < screenWidth / 2f) "brightness" else "volume"
                            }
                        }

                        when (dragDirection) {
                            "seek" -> {
                                isDraggingSeek = true
                                val sweepMultiplier = 120000f / screenWidth
                                val deltaMs = (dx * sweepMultiplier).toLong()
                                dragSeekOffset = deltaMs
                                dragSeekPosition = (initialPositionForSeek + deltaMs).coerceIn(0L, durationState.coerceAtLeast(1L))
                                tempSeekPosition = dragSeekPosition
                            }
                            "brightness" -> {
                                isDraggingBrightness = true
                                val delta = -dy / screenHeight
                                val targetBrightness = (initialBrightness + delta).coerceIn(0.01f, 1.0f)
                                currentBrightness = targetBrightness
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = targetBrightness
                                    act.window.attributes = lp
                                }
                            }
                            "volume" -> {
                                isDraggingVolume = true
                                val delta = -dy / screenHeight
                                val targetVolume = (initialVolume + (delta * maxVolume).toInt()).coerceIn(0, maxVolume)
                                currentVolume = targetVolume
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            } finally {
                isPointerActive = false
                holdJob.cancel()
                if (isLongPressActive || isFastForward2x) {
                    isLongPressActive = false
                    isFastForward2x = false
                    exoPlayerState?.setPlaybackSpeed(currentSpeedState)
                }
            }

            // Finger released - handle tap / double tap only if not long-pressed or dragged
            val elapsedTotal = System.currentTimeMillis() - startTime
            if (!wasLongPressActive && !hasMoved && elapsedTotal < 350) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 350 && abs(startX - lastTapX) < 120f) {
                    // Double Tap Detected!
                    if (startX < screenWidth / 2f) {
                        // Left side double tap -> Rewind 10s
                        val target = (currentPositionState - 10000L).coerceAtLeast(0L)
                        exoPlayerState?.seekTo(target)
                        currentPosition = target
                        showLeftDoubleTapAnim = true
                        lastTapTime = 0L
                    } else {
                        // Right side double tap -> Forward 10s
                        val target = (currentPositionState + 10000L).coerceIn(0L, durationState.coerceAtLeast(1L))
                        exoPlayerState?.seekTo(target)
                        currentPosition = target
                        showRightDoubleTapAnim = true
                        lastTapTime = 0L
                    }
                } else {
                    lastTapTime = now
                    lastTapX = startX
                    showControls = !showControls
                }
            }

            if (isDraggingSeek) {
                isDraggingSeek = false
                if (durationState > 0) {
                    exoPlayerState?.seekTo(dragSeekPosition)
                    currentPosition = dragSeekPosition
                }
            }
            isDraggingBrightness = false
            isDraggingVolume = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(gestureModifier)
    ) {
        // Render ExoPlayer view
        AndroidView(
            factory = { ctx ->
                val view = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.exo_player_texture_view, null) as PlayerView
                view.apply {
                    keepScreenOn = true
                    useController = false
                    this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    subtitleView?.apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                        setApplyEmbeddedStyles(true)
                        setApplyEmbeddedFontSizes(true)
                        setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                    }
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeMode
                view.subtitleView?.visibility = if (isSubtitlesEnabled) android.view.View.VISIBLE else android.view.View.GONE
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading and Buffering Indicator (Removed to prevent spinners on screen, as requested)

        // Overlay 1: Blinking 2X Fast Forward HUD (Transparent with no background card)
        AnimatedVisibility(
            visible = isFastForward2x,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "2X Speed",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "» 2X Fast Forwarding",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Overlay 2: Seek HUD Indicator (Transparent with no background card)
        AnimatedVisibility(
            visible = isDraggingSeek,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (dragSeekOffset >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${formatTime(tempSeekPosition)} / ${formatTime(duration)}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val deltaSeconds = (dragSeekOffset / 1000).toInt()
                val sign = if (deltaSeconds >= 0) "+" else ""
                Text(
                    text = "$sign${deltaSeconds}s",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Overlay 3: Volume HUD Indicator
        AnimatedVisibility(
            visible = isDraggingVolume,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (currentVolume == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(currentVolume.toFloat() / maxVolume.coerceAtLeast(1).toFloat())
                                .align(Alignment.BottomCenter)
                                .background(NeonCyan)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(currentVolume * 100 / maxVolume.coerceAtLeast(1))}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay 4: Brightness HUD Indicator
        AnimatedVisibility(
            visible = isDraggingBrightness,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 40.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Brightness5,
                        contentDescription = "Brightness",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(currentBrightness)
                                .align(Alignment.BottomCenter)
                                .background(NeonCyan)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(currentBrightness * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay 5: Subtitle HUD Status Indicator
        AnimatedVisibility(
            visible = subtitleHudMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isFullScreen) 40.dp else 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, if (isSubtitlesEnabled) NeonCyan.copy(alpha = 0.7f) else NeonMagenta.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSubtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                        contentDescription = null,
                        tint = if (isSubtitlesEnabled) NeonCyan else NeonMagenta,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = subtitleHudMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay 5: Floating Next Episode Button (Series, Full Screen, Last 2 mins)
        val remainingTimeMs = duration - currentPosition
        val twoMinutesMs = 2 * 60 * 1000L
        val showNextButton = isSeries && isFullScreen && duration > 0 && remainingTimeMs <= twoMinutesMs

        if (showNextButton && onNextEpisode != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 88.dp, end = 24.dp)
            ) {
                Button(
                    onClick = onNextEpisode,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Next Episode",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Episode",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Left Double Tap Indicator (-10s, clean text without semi-transparent background card)
        AnimatedVisibility(
            visible = showLeftDoubleTapAnim,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = if (isFullScreen) 80.dp else 40.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "-10s",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Right Double Tap Indicator (+10s, clean text without semi-transparent background card)
        AnimatedVisibility(
            visible = showRightDoubleTapAnim,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (isFullScreen) 80.dp else 40.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+10s",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Screen Lock/Unlock overlay button (Only in Full Screen Mode)
        if (isFullScreen) {
            AnimatedVisibility(
                visible = showControls || isScreenLocked,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        isScreenLocked = !isScreenLocked
                        showControls = !isScreenLocked // Keep controls open if unlocking, hide them if locking
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, NeonCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Screen Lock",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Overlay 6: Playback Controls (Timeline & Seek, Play/Pause, Quality Settings)
        AnimatedVisibility(
            visible = showControls && !isFastForward2x && !isScreenLocked && !isInPipMode,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            ) {
                // Top controls bar (NO BACK BUTTON as requested!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .then(
                            if (isFullScreen) Modifier.statusBarsPadding().displayCutoutPadding().padding(horizontal = 8.dp, vertical = 6.dp)
                            else Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 2.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = channelName,
                        color = Color.White,
                        fontSize = if (isFullScreen) 15.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isFullScreen) 16.dp else 8.dp)
                    ) {
                        // Subtitles ON / OFF & Language Selector Button
                        IconButton(
                            onClick = {
                                if (subtitles.isEmpty()) {
                                    isSubtitlesEnabled = !isSubtitlesEnabled
                                    subtitleHudMessage = if (isSubtitlesEnabled) "Subtitles: ON" else "Subtitles: OFF"
                                } else if (subtitles.size == 1) {
                                    isSubtitlesEnabled = !isSubtitlesEnabled
                                    subtitleHudMessage = if (isSubtitlesEnabled) "Subtitles: ${subtitles.first().label}" else "Subtitles: OFF"
                                } else {
                                    showSubtitleMenu = true
                                }
                            },
                            modifier = Modifier
                                .size(if (isFullScreen) 32.dp else 28.dp)
                                .testTag("subtitle_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isSubtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Subtitle Toggle",
                                tint = if (isSubtitlesEnabled) NeonCyan else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(if (isFullScreen) 20.dp else 18.dp)
                            )
                        }

                        // Playback Speed Selector Button
                        IconButton(
                            onClick = { showSpeedMenu = true },
                            modifier = Modifier
                                .size(if (isFullScreen) 32.dp else 28.dp)
                                .testTag("playback_speed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SlowMotionVideo,
                                contentDescription = "Playback Speed",
                                tint = if (playbackSpeed != 1.0f) NeonCyan else Color.White,
                                modifier = Modifier.size(if (isFullScreen) 20.dp else 18.dp)
                            )
                        }

                        // Settings Gear Button (Quality & Server Selection)
                        IconButton(
                            onClick = {
                                settingsMenuPage = "MAIN"
                                showSettingsMenu = true
                            },
                            modifier = Modifier
                                .size(if (isFullScreen) 32.dp else 28.dp)
                                .testTag("player_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Playback Settings",
                                tint = Color.White,
                                modifier = Modifier.size(if (isFullScreen) 20.dp else 18.dp)
                            )
                        }

                        // Floating Player PiP Button
                        IconButton(
                            onClick = {
                                onMiniPlayerToggle()
                            },
                            modifier = Modifier
                                .size(if (isFullScreen) 32.dp else 28.dp)
                                .testTag("floating_pip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Floating Player Mode",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(if (isFullScreen) 20.dp else 18.dp)
                            )
                        }

                        // Fullscreen Toggle
                        IconButton(
                            onClick = onFullScreenToggle,
                            modifier = Modifier.size(if (isFullScreen) 32.dp else 28.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(if (isFullScreen) 22.dp else 20.dp)
                            )
                        }
                    }
                }

                // Center Play/Pause Indicator Button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (isFullScreen) 58.dp else 44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.5.dp, NeonCyan, CircleShape)
                        .clickable {
                            exoPlayer?.let { player ->
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = NeonCyan,
                        modifier = Modifier.size(if (isFullScreen) 30.dp else 24.dp)
                    )
                }

                // Bottom Timeline & Progress slider bar (Cleanly docked to bottom edge in non-fullscreen)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .then(
                            if (isFullScreen) Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)
                            else Modifier.padding(start = 10.dp, top = 0.dp, end = 10.dp, bottom = 12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = if (isFullScreen) 11.sp else 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.LightGray,
                            fontSize = if (isFullScreen) 11.sp else 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val progress = if (duration > 0) {
                        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = progress,
                        onValueChange = { frac ->
                            isDraggingSlider = true
                            if (duration > 0) {
                                currentPosition = (frac * duration).toLong()
                            }
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            exoPlayer?.seekTo(currentPosition)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isFullScreen) 20.dp else 14.dp)
                    )
                }
            }
        }

        // Compact Multi-Option Settings Dialog Popup (Quality & Servers)
        if (showSettingsMenu) {
            Dialog(onDismissRequest = { showSettingsMenu = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        when (settingsMenuPage) {
                            "MAIN" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Playback Settings",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    IconButton(
                                        onClick = { showSettingsMenu = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Option 1: Quality
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { settingsMenuPage = "QUALITY" }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HighQuality,
                                            contentDescription = "Quality",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Quality",
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (selectedQuality == "Auto") "Auto (Best)" else selectedQuality,
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Open Quality",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Option 2: Servers
                                val currentServerName = if (isAnime) {
                                    val type = selectedAnikotoServer?.type?.uppercase() ?: "SUB"
                                    val name = selectedAnikotoServer?.name ?: "Server 1"
                                    "$type: $name"
                                } else {
                                    val effectiveIdx = if (embedServers.isNotEmpty()) currentEmbedServerIndex % embedServers.size else 0
                                    embedServers.getOrNull(effectiveIdx)?.first?.substringBefore(" (") ?: "Direct Server"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { settingsMenuPage = "SERVERS" }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Dns,
                                            contentDescription = "Servers",
                                            tint = NeonPurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Servers",
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = currentServerName,
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Open Servers",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Option 3: Floating Player Mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable {
                                            showSettingsMenu = false
                                            onMiniPlayerToggle()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureInPictureAlt,
                                            contentDescription = "Floating Player Mode",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Floating Player Mode",
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Pop out into floating window player",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "PiP",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            "QUALITY" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { settingsMenuPage = "MAIN" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Select Quality",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                val qualities = listOf("Auto", "1080p", "720p", "480p")
                                qualities.forEach { quality ->
                                    val isSelected = selectedQuality == quality
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedQuality = quality
                                                subtitleHudMessage = "Quality: $quality"
                                                showSettingsMenu = false
                                            }
                                            .background(if (isSelected) NeonCyan.copy(alpha = 0.08f) else Color.Transparent)
                                            .padding(vertical = 12.dp, horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (quality == "Auto") "Auto (Best)" else quality,
                                            color = if (isSelected) NeonCyan else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            "SERVERS" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { settingsMenuPage = "MAIN" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Select Server",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                if (isAnime) {
                                    // Anime SUB & DUB Servers
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                    ) {
                                        // SUB Servers section
                                        if (subServers.isNotEmpty()) {
                                            Text(
                                                text = "SUBTITLED (SUB)",
                                                color = NeonCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(subServers) { srv ->
                                                    val isSelected = selectedAnikotoServer?.linkId == srv.linkId ||
                                                            (selectedAnikotoServer == null && srv == subServers.firstOrNull())
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            onSelectAnikotoServer?.invoke(srv)
                                                            subtitleHudMessage = "Server: SUB ${srv.name}"
                                                            showSettingsMenu = false
                                                        },
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
                                                            containerColor = Color.White.copy(alpha = 0.08f),
                                                            labelColor = Color.White
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // DUB Servers section
                                        if (dubServers.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                text = "DUBBED (DUB)",
                                                color = NeonPurple,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(dubServers) { srv ->
                                                    val isSelected = selectedAnikotoServer?.linkId == srv.linkId
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            onSelectAnikotoServer?.invoke(srv)
                                                            subtitleHudMessage = "Server: DUB ${srv.name}"
                                                            showSettingsMenu = false
                                                        },
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
                                                            containerColor = Color.White.copy(alpha = 0.08f),
                                                            labelColor = Color.White
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Movie & TV Series Servers list
                                    val serverList = if (embedServers.isNotEmpty()) embedServers else listOf(Pair("Direct Fast Stream", ""))
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        itemsIndexed(serverList) { index, server ->
                                            val isSelected = index == (currentEmbedServerIndex % serverList.size)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        onSelectEmbedServerIndex?.invoke(index)
                                                        subtitleHudMessage = "Server: ${server.first}"
                                                        showSettingsMenu = false
                                                    }
                                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.12f) else Color.Transparent)
                                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = server.first,
                                                    color = if (isSelected) NeonCyan else Color.White,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = NeonCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtitles selection dialog popup
        if (showSubtitleMenu) {
            Dialog(onDismissRequest = { showSubtitleMenu = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Subtitles & Captions",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select language or toggle subtitles",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Option 1: Off
                        val isOff = !isSubtitlesEnabled
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    isSubtitlesEnabled = false
                                    subtitleHudMessage = "Subtitles: OFF"
                                    showSubtitleMenu = false
                                }
                                .background(if (isOff) NeonMagenta.copy(alpha = 0.12f) else Color.Transparent)
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Off (Disabled)",
                                color = if (isOff) NeonMagenta else Color.White,
                                fontWeight = if (isOff) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isOff) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = NeonMagenta,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Subtitle options
                        val trackList = if (subtitles.isNotEmpty()) {
                            subtitles.distinctBy { it.lang.lowercase() + it.label.lowercase() }
                        } else {
                            listOf(com.example.scraper.SubtitleTrack(url = "", lang = "en", label = "English (Auto)"))
                        }

                        trackList.forEach { track ->
                            val isSelected = isSubtitlesEnabled && (selectedSubtitleLang == track.lang || (selectedSubtitleLang == null && track.default))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        isSubtitlesEnabled = true
                                        selectedSubtitleLang = track.lang
                                        subtitleHudMessage = "Subtitles: ${track.label}"
                                        showSubtitleMenu = false
                                    }
                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.08f) else Color.Transparent)
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = track.label,
                                    color = if (isSelected) NeonCyan else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Playback speed selection dialog popup
        if (showSpeedMenu) {
            Dialog(onDismissRequest = { showSpeedMenu = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Playback Speed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select video playback speed",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        speeds.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            val speedLabel = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer?.setPlaybackSpeed(speed)
                                        subtitleHudMessage = "Speed: ${speed}x"
                                        showSpeedMenu = false
                                    }
                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.08f) else Color.Transparent)
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = speedLabel,
                                    color = if (isSelected) NeonCyan else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDownloaderDialog) {
            com.example.ui.components.DownloaderModal(
                showModal = showDownloaderDialog,
                onDismiss = { showDownloaderDialog = false },
                title = channelName,
                imdbId = if (isAnime) "anikoto_$channelName" else channelName,
                isAnime = isAnime,
                capturedVideoUrl = currentUrl,
                coroutineScope = scope
            )
        }
    }
}

private fun formatTime(ms: Long): String {
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
