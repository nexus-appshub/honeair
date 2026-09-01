package com.example.ui.components

import android.app.Activity
import android.content.Context
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.example.network.SmartNetworkBoosterEngine
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.BorderColor
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SpaceBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun MovieExoPlayerView(
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
    onProgressUpdate: (Long, Long) -> Unit = { _, _ -> }
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

    // Position and Timeline State
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    // Settings & Qualities State
    var selectedQuality by remember { mutableStateOf("Auto") }
    var showQualityMenu by remember { mutableStateOf(false) }

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

    LaunchedEffect(currentUrl, customHeaders, subtitles) {
        errorMessage = null
        exoPlayer?.release()

        val httpDataSourceFactory = SmartNetworkBoosterEngine.createBoostedHttpDataSourceFactory(
            customHeaders = customHeaders,
            url = currentUrl
        )
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)
        val loadControl = SmartNetworkBoosterEngine.createDynamicLoadControl()

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
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

                if (subtitles.isNotEmpty()) {
                    val subtitleConfigs = subtitles.map { sub ->
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
                    mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
                }

                setMediaItem(mediaItemBuilder.build())
                prepare()
                if (initialStartPositionMs > 0L) {
                    seekTo(initialStartPositionMs)
                }
                playWhenReady = true
            }

        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    errorMessage = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError(error.message ?: "Playback error encountered.")
            }
        })

        exoPlayer = player
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

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
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

    // Gesture pointer logic block
    val gestureModifier = Modifier.pointerInput(duration, currentPosition, isFullScreen, isScreenLocked) {
        awaitEachGesture {
            if (isScreenLocked) {
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
            val startTime = System.currentTimeMillis()
            val startX = down.position.x
            val startY = down.position.y
            val screenWidth = size.width
            val screenHeight = size.height

            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            initialBrightness = activity?.window?.attributes?.screenBrightness?.let { if (it < 0) 0.5f else it } ?: 0.5f
            initialPositionForSeek = currentPosition

            var dragDirection: String? = null
            var hasMoved = false
            var isLongPress = false
            var dragSeekPosition = currentPosition

            while (true) {
                val event = awaitPointerEvent()
                val anyActive = event.changes.any { it.pressed }
                if (!anyActive) break

                val touch = event.changes.first()
                val elapsed = System.currentTimeMillis() - startTime

                // Trigger 2X Speed Hold after 500ms
                if (!hasMoved && elapsed > 500 && !isLongPress) {
                    isLongPress = true
                    isFastForward2x = true
                    exoPlayer?.setPlaybackSpeed(2.0f)
                }

                val dx = touch.position.x - startX
                val dy = touch.position.y - startY

                if (abs(dx) > 18f || abs(dy) > 18f) {
                    hasMoved = true
                }

                if (hasMoved && !isLongPress) {
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
                            dragSeekPosition = (initialPositionForSeek + deltaMs).coerceIn(0L, duration.coerceAtLeast(1L))
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

            // Finger released
            val elapsedTotal = System.currentTimeMillis() - startTime
            if (isLongPress) {
                isFastForward2x = false
                exoPlayer?.setPlaybackSpeed(1.0f)
            } else if (!hasMoved && elapsedTotal < 350) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 350 && abs(startX - lastTapX) < 120f) {
                    // Double Tap Detected!
                    if (startX < screenWidth / 2f) {
                        // Left side double tap -> Rewind 10s
                        val target = (currentPosition - 10000L).coerceAtLeast(0L)
                        exoPlayer?.seekTo(target)
                        currentPosition = target
                        showLeftDoubleTapAnim = true
                        lastTapTime = 0L
                    } else {
                        // Right side double tap -> Forward 10s
                        val target = (currentPosition + 10000L).coerceIn(0L, duration.coerceAtLeast(1L))
                        exoPlayer?.seekTo(target)
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
                if (duration > 0) {
                    exoPlayer?.seekTo(dragSeekPosition)
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
                PlayerView(ctx).apply {
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

        // Loading and Buffering Indicator
        if (playbackState == Player.STATE_BUFFERING && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

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
            visible = showControls && !isFastForward2x && !isScreenLocked,
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
                            if (isFullScreen) Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)
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
                                showSubtitleMenu = true
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

                        // Settings Gear Button for Quality selection
                        IconButton(
                            onClick = { showQualityMenu = true },
                            modifier = Modifier.size(if (isFullScreen) 32.dp else 28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Quality Selection",
                                tint = Color.White,
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

        // Quality selection dialog popup
        if (showQualityMenu) {
            Dialog(onDismissRequest = { showQualityMenu = false }) {
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
                            text = "Stream Video Quality",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select max playback resolution",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val qualities = listOf("Auto", "1080p", "720p", "480p")
                        qualities.forEach { quality ->
                            val isSelected = selectedQuality == quality
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedQuality = quality
                                        showQualityMenu = false
                                    }
                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.08f) else Color.Transparent)
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
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
                }
            }
        }

        // Subtitles selection dialog popup (Small & Compact Popup Window)
        if (showSubtitleMenu) {
            var subtitleFilterQuery by remember { mutableStateOf("") }
            val distinctTracks = remember(subtitles) {
                if (subtitles.isNotEmpty()) {
                    subtitles.distinctBy { it.url }
                } else emptyList()
            }
            val filteredTrackList = remember(distinctTracks, subtitleFilterQuery) {
                if (subtitleFilterQuery.isBlank()) {
                    distinctTracks
                } else {
                    distinctTracks.filter {
                        it.label.contains(subtitleFilterQuery, ignoreCase = true) ||
                        it.lang.contains(subtitleFilterQuery, ignoreCase = true)
                    }
                }
            }

            Dialog(onDismissRequest = { showSubtitleMenu = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.5f), NeonMagenta.copy(alpha = 0.3f)))),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    modifier = Modifier
                        .width(310.dp)
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header with icon, title & close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (distinctTracks.isNotEmpty()) "Subtitles (${distinctTracks.size})" else "Subtitles & CC",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }

                            IconButton(
                                onClick = { showSubtitleMenu = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Search Filter Bar (if 5 or more subtitles available)
                        if (distinctTracks.size >= 5) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF202024))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    BasicTextField(
                                        value = subtitleFilterQuery,
                                        onValueChange = { subtitleFilterQuery = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(NeonCyan),
                                        decorationBox = { innerTextField ->
                                            if (subtitleFilterQuery.isEmpty()) {
                                                Text(
                                                    text = "Search language (Bangla, Eng...)",
                                                    color = Color.Gray,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (subtitleFilterQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.Gray,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { subtitleFilterQuery = "" }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Option 1: "Subtitles Off"
                        val isOff = !isSubtitlesEnabled
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    isSubtitlesEnabled = false
                                    selectedSubtitleLang = null
                                    subtitleHudMessage = "Subtitles: OFF"
                                    showSubtitleMenu = false
                                }
                                .background(if (isOff) NeonMagenta.copy(alpha = 0.15f) else Color(0xFF1E1E22))
                                .border(
                                    1.dp,
                                    if (isOff) NeonMagenta.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(vertical = 9.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SubtitlesOff,
                                    contentDescription = null,
                                    tint = if (isOff) NeonMagenta else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Turn Off Subtitles",
                                    color = if (isOff) NeonMagenta else Color.White.copy(alpha = 0.85f),
                                    fontWeight = if (isOff) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                            if (isOff) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = NeonMagenta,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subtitle List (Compact Scrollable Container)
                        if (distinctTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1B1B1E))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Searching available subtitles...",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    CircularProgressIndicator(
                                        color = NeonCyan,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        } else if (filteredTrackList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching language found",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredTrackList) { track ->
                                    val isSelected = isSubtitlesEnabled && (
                                        selectedSubtitleLang == track.lang || 
                                        (selectedSubtitleLang == null && track.default) ||
                                        (selectedSubtitleLang == track.label)
                                    )
                                    
                                    val isBanglaTrack = track.lang.startsWith("bn", ignoreCase = true) || 
                                        track.label.contains("Bengali", ignoreCase = true) || 
                                        track.label.contains("বাংলা")
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                isSubtitlesEnabled = true
                                                selectedSubtitleLang = track.lang
                                                subtitleHudMessage = "Subtitles: ${track.label}"
                                                showSubtitleMenu = false
                                            }
                                            .background(
                                                if (isSelected) NeonCyan.copy(alpha = 0.14f)
                                                else if (isBanglaTrack) Color(0xFF10B981).copy(alpha = 0.08f)
                                                else Color(0xFF1E1E22)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) NeonCyan.copy(alpha = 0.6f)
                                                else if (isBanglaTrack) Color(0xFF10B981).copy(alpha = 0.3f)
                                                else Color.Transparent,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(vertical = 9.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ClosedCaption,
                                                contentDescription = null,
                                                tint = if (isSelected) NeonCyan else if (isBanglaTrack) Color(0xFF10B981) else Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            
                                            Column {
                                                Text(
                                                    text = track.label,
                                                    color = if (isSelected) NeonCyan else Color.White,
                                                    fontWeight = if (isSelected || isBanglaTrack) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (track.lang.isNotBlank() && track.lang != "en" && !track.label.contains(track.lang, ignoreCase = true)) {
                                                    Text(
                                                        text = track.lang.uppercase(),
                                                        color = Color.Gray,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            val formatTag = when {
                                                track.url.contains(".vtt", ignoreCase = true) -> "VTT"
                                                track.url.contains(".srt", ignoreCase = true) -> "SRT"
                                                track.url.contains(".ass", ignoreCase = true) -> "ASS"
                                                else -> "CC"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.White.copy(alpha = 0.08f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = formatTag,
                                                    color = Color.Gray,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
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
