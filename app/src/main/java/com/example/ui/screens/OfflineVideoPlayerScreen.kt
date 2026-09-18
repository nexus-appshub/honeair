package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.network.SmartNetworkBoosterEngine
import kotlinx.coroutines.delay
import java.io.File

/**
 * High-performance, hardware-accelerated Offline Video Player
 * with touch-hold 2x speed boost, double tap +/-10s seek,
 * swipe volume/brightness/seeking gestures, speed selection (0.5x - 3.0x),
 * and non-fullscreen local video playlist list.
 */
@OptIn(UnstableApi::class)
@Composable
fun OfflineVideoPlayerScreen(
    videoFile: File,
    playlist: List<File> = emptyList(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Playlist management for downloaded files
    var currentFile by remember { mutableStateOf(videoFile) }
    val downloadedFiles = remember(currentFile, playlist) {
        if (playlist.isNotEmpty()) {
            playlist.distinctBy { it.absolutePath }
        } else {
            val dirs = listOfNotNull(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                context.filesDir,
                context.cacheDir
            )
            dirs.flatMap { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.filter {
                        it.isFile && (
                            it.name.endsWith(".mp4", true) ||
                            it.name.endsWith(".mkv", true) ||
                            it.name.endsWith(".webm", true) ||
                            it.name.endsWith(".ts", true)
                        )
                    } ?: emptyList()
                } else {
                    emptyList()
                }
            }.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
        }.ifEmpty { listOf(currentFile) }
    }

    val currentFileIndex = downloadedFiles.indexOfFirst { it.absolutePath == currentFile.absolutePath }
    val hasPrevious = currentFileIndex > 0
    val hasNext = currentFileIndex >= 0 && currentFileIndex < downloadedFiles.size - 1

    var isFullscreen by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var useSoftwareFallback by remember { mutableStateOf(false) }

    // Speed states
    var baseSpeed by remember { mutableFloatStateOf(1.0f) }
    var is2xBoosting by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Gesture indicator overlay states
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }
    var gestureOverlayIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }

    // Display title cleaned from filename
    val displayTitle = remember(currentFile) {
        currentFile.nameWithoutExtension
            .replace(Regex("[_\\-\\.]+"), " ")
            .replace(Regex("(?i)(1080p|720p|480p|bluray|web-dl|x264|x265|hevc|aac)"), "")
            .trim()
            .ifBlank { currentFile.name }
    }

    val renderersFactory = remember(useSoftwareFallback) {
        val sharedPrefs = context.getSharedPreferences("stream_app_prefs", Context.MODE_PRIVATE)
        val userDecoderIndex = if (useSoftwareFallback) 2 else sharedPrefs.getInt("setting_decoder_index", 0)
        val isHwAccel = if (useSoftwareFallback) false else sharedPrefs.getBoolean("setting_hardware_accel", true)
        SmartNetworkBoosterEngine.createRenderersFactory(
            context = context,
            decoderMode = userDecoderIndex,
            isHardwareAccelerated = isHwAccel
        )
    }

    val exoPlayer = remember(renderersFactory) {
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            context,
            extractorsFactory
        )

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    // Synchronize player speed
    LaunchedEffect(baseSpeed, is2xBoosting, exoPlayer) {
        val targetSpeed = if (is2xBoosting) 2.0f else baseSpeed
        exoPlayer.setPlaybackSpeed(targetSpeed)
    }

    var detectedMime by remember(currentFile) { mutableStateOf<String?>(null) }

    LaunchedEffect(currentFile) {
        try {
            if (currentFile.exists() && currentFile.length() > 0L) {
                java.io.FileInputStream(currentFile).use { fis ->
                    val header = ByteArray(188)
                    val read = fis.read(header)
                    if (read > 0 && header[0] == 0x47.toByte()) {
                        detectedMime = androidx.media3.common.MimeTypes.VIDEO_MP2T
                    }
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(currentFile, detectedMime, exoPlayer) {
        playbackError = null
        if (!currentFile.exists() || currentFile.length() <= 0L) {
            playbackError = "Offline video file not found or empty."
            isBuffering = false
            return@LaunchedEffect
        }
        try {
            val uri = Uri.fromFile(currentFile)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (detectedMime != null) {
                mediaItemBuilder.setMimeType(detectedMime)
            }

            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            playbackError = "Error initializing playback: ${e.message}"
            isBuffering = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (detectedMime != null) {
                    detectedMime = null
                } else if (!useSoftwareFallback) {
                    useSoftwareFallback = true
                } else {
                    playbackError = "Playback error: ${error.message ?: "Format issue"}"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    BackHandler {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isFullscreen = false
        } else {
            onBack()
        }
    }

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

    // Audio and Brightness Helper Logic
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    // Helper functions for gestures
    fun adjustVolume(deltaY: Float) {
        if (audioManager == null) return
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = if (deltaY < 0) 1 else -1
        val newVol = (currentVol + step).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        val pct = ((newVol.toFloat() / maxVolume.toFloat()) * 100).toInt()
        gestureOverlayText = "Volume $pct%"
        gestureOverlayIcon = if (newVol == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp
    }

    fun adjustBrightness(deltaY: Float) {
        val act = activity ?: return
        val lp = act.window.attributes
        var currentB = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        val change = if (deltaY < 0) 0.05f else -0.05f
        currentB = (currentB + change).coerceIn(0.05f, 1.0f)
        lp.screenBrightness = currentB
        act.window.attributes = lp
        val pct = (currentB * 100).toInt()
        gestureOverlayText = "Brightness $pct%"
        gestureOverlayIcon = Icons.Default.Brightness6
    }

    // Speed Selection Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    baseSpeed = speed
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${speed}x" + if (speed == 1.0f) " (Normal)" else "",
                                color = if (baseSpeed == speed) Color(0xFFFF9800) else Color.White,
                                fontWeight = if (baseSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            )
                            if (baseSpeed == speed) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFF9800))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = Color(0xFFFF9800))
                }
            },
            containerColor = Color(0xFF1E222D)
        )
    }

    if (isFullscreen) {
        // Landscape Full-Screen Immersive Mode
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (tryAwaitRelease()) {
                                is2xBoosting = false
                            } else {
                                is2xBoosting = false
                            }
                        },
                        onDoubleTap = { offset ->
                            val width = size.width
                            if (offset.x < width * 0.35f) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                                gestureOverlayText = "-10s"
                                gestureOverlayIcon = Icons.Default.Replay10
                            } else if (offset.x > width * 0.65f) {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                gestureOverlayText = "+10s"
                                gestureOverlayIcon = Icons.Default.Forward10
                            } else {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        },
                        onTap = { showControls = !showControls },
                        onLongPress = {
                            is2xBoosting = true
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            is2xBoosting = false
                            gestureOverlayText = null
                            gestureOverlayIcon = null
                        },
                        onDragCancel = {
                            is2xBoosting = false
                            gestureOverlayText = null
                            gestureOverlayIcon = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            if (kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)) {
                                if (change.position.x < width / 2) {
                                    adjustBrightness(dragAmount.y)
                                } else {
                                    adjustVolume(dragAmount.y)
                                }
                            } else {
                                val seekDelta = (dragAmount.x * 200).toLong()
                                val target = (exoPlayer.currentPosition + seekDelta).coerceIn(0L, exoPlayer.duration.coerceAtLeast(1L))
                                exoPlayer.seekTo(target)
                                gestureOverlayText = formatOfflineDuration(target)
                                gestureOverlayIcon = Icons.Default.FastForward
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv -> pv.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize()
            )

            // 2X Speed Boost Indicator
            if (is2xBoosting) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("2.0X SPEED BOOST", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Swipe / Gesture Overlay Indicator
            if (gestureOverlayText != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        gestureOverlayIcon?.let { icon ->
                            Icon(icon, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(gestureOverlayText!!, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            CompactOfflinePlayerControls(
                title = displayTitle,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                showControls = showControls,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                isFullscreen = true,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                currentSpeed = baseSpeed,
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { exoPlayer.seekTo(it) },
                onRewind10 = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
                onForward10 = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                onPreviousVideo = { if (hasPrevious) currentFile = downloadedFiles[currentFileIndex - 1] },
                onNextVideo = { if (hasNext) currentFile = downloadedFiles[currentFileIndex + 1] },
                onToggleFullscreen = { isFullscreen = false },
                onOpenSpeedDialog = { showSpeedDialog = true },
                onToggleAspectRatio = {
                    resizeMode = when (resizeMode) {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onBack = { isFullscreen = false }
            )
        }
    } else {
        // Portrait Mode with Player Viewport + Non-Fullscreen Local Video List
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp),
            containerColor = Color(0xFF0B0D14)
        ) { paddingValues ->
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val effectiveTopPadding = if (statusBarTop > 0.dp) statusBarTop + 4.dp else 24.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = effectiveTopPadding)
                    .navigationBarsPadding()
            ) {
                // 1. Compact 16:9 Video Player Viewport with Gesture Support
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (tryAwaitRelease()) {
                                        is2xBoosting = false
                                    } else {
                                        is2xBoosting = false
                                    }
                                },
                                onDoubleTap = { offset ->
                                    val width = size.width
                                    if (offset.x < width * 0.35f) {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                                        gestureOverlayText = "-10s"
                                        gestureOverlayIcon = Icons.Default.Replay10
                                    } else if (offset.x > width * 0.65f) {
                                        exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                        gestureOverlayText = "+10s"
                                        gestureOverlayIcon = Icons.Default.Forward10
                                    } else {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                },
                                onTap = { showControls = !showControls },
                                onLongPress = { is2xBoosting = true }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    is2xBoosting = false
                                    gestureOverlayText = null
                                    gestureOverlayIcon = null
                                },
                                onDragCancel = {
                                    is2xBoosting = false
                                    gestureOverlayText = null
                                    gestureOverlayIcon = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val width = size.width
                                    if (kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)) {
                                        if (change.position.x < width / 2) {
                                            adjustBrightness(dragAmount.y)
                                        } else {
                                            adjustVolume(dragAmount.y)
                                        }
                                    } else {
                                        val seekDelta = (dragAmount.x * 200).toLong()
                                        val target = (exoPlayer.currentPosition + seekDelta).coerceIn(0L, exoPlayer.duration.coerceAtLeast(1L))
                                        exoPlayer.seekTo(target)
                                        gestureOverlayText = formatOfflineDuration(target)
                                        gestureOverlayIcon = Icons.Default.FastForward
                                    }
                                }
                            )
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                this.resizeMode = resizeMode
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { pv -> pv.resizeMode = resizeMode },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2X Speed Boost Pill
                    if (is2xBoosting) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("2.0X SPEED BOOST", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    // Gesture Overlay Text
                    if (gestureOverlayText != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                gestureOverlayIcon?.let { icon ->
                                    Icon(icon, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(gestureOverlayText!!, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    CompactOfflinePlayerControls(
                        title = displayTitle,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        showControls = showControls,
                        currentPosition = currentPosition,
                        totalDuration = totalDuration,
                        isFullscreen = false,
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                        currentSpeed = baseSpeed,
                        onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        onSeek = { exoPlayer.seekTo(it) },
                        onRewind10 = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
                        onForward10 = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                        onPreviousVideo = { if (hasPrevious) currentFile = downloadedFiles[currentFileIndex - 1] },
                        onNextVideo = { if (hasNext) currentFile = downloadedFiles[currentFileIndex + 1] },
                        onToggleFullscreen = { isFullscreen = true },
                        onOpenSpeedDialog = { showSpeedDialog = true },
                        onToggleAspectRatio = {
                            resizeMode = when (resizeMode) {
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        onBack = onBack
                    )
                }

                // 2. Non-Fullscreen Imported Local Offline Video Playlist List
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "OFFLINE PLAYLIST (${downloadedFiles.size} VIDEOS)",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        TextButton(onClick = { showSpeedDialog = true }) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speed: ${baseSpeed}x", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Imported Video Playlist",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(downloadedFiles) { file ->
                            val isCurrent = file.absolutePath == currentFile.absolutePath
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) Color(0xFF1E293B) else Color(0xFF141721),
                                border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentFile = file }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.PlayCircle else Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color(0xFFFF9800) else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.nameWithoutExtension,
                                            color = if (isCurrent) Color(0xFFFF9800) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${file.length() / (1024 * 1024)} MB • ${file.extension.uppercase()}",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (isCurrent) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFF9800).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "PLAYING",
                                                color = Color(0xFFFF9800),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

/**
 * Compact Player Controls Overlay
 */
@Composable
fun CompactOfflinePlayerControls(
    title: String = "",
    isPlaying: Boolean,
    isBuffering: Boolean,
    showControls: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    isFullscreen: Boolean,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    currentSpeed: Float = 1.0f,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onBack: () -> Unit
) {
    AnimatedVisibility(
        visible = showControls || isBuffering,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            // Top Bar: Back button, Title, Speed, Aspect Ratio & Fullscreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .then(
                        if (isFullscreen) Modifier.statusBarsPadding().displayCutoutPadding()
                        else Modifier
                    )
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSpeedDialog,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed Settings",
                            tint = if (currentSpeed != 1.0f) Color(0xFFFF9800) else Color.White
                        )
                    }

                    IconButton(
                        onClick = onToggleAspectRatio,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White
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
            }

            // Center Controls: Prev Video, Rewind 10s, Play/Pause / Buffering, Forward 10s, Next Video
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (hasPrevious) {
                    IconButton(
                        onClick = onPreviousVideo,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Video",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRewind10,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(0xFFFF9800),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onForward10,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (hasNext) {
                    IconButton(
                        onClick = onNextVideo,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Video",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Bottom Bar: Position, Seekbar, Duration
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatOfflineDuration(currentPosition),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = formatOfflineDuration(totalDuration),
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp
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
                        .height(24.dp)
                )
            }
        }
    }
}

private fun formatOfflineDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

