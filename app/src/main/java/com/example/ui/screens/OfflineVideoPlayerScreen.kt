package com.example.ui.screens

import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpaceBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned

@OptIn(UnstableApi::class)
@Composable
fun OfflineVideoPlayerScreen(
    videoFile: File,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Stateful video file to allow skipping to next downloaded video!
    var currentVideoFile by remember { mutableStateOf(videoFile) }

    var showPlayerSettingsDialog by remember { mutableStateOf(false) }

    // Helper to fetch downloaded files
    fun getDownloadedFiles(ctx: Context): List<File> {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir == null || !dir.exists()) return emptyList()
        return dir.listFiles()?.filter {
            it.isFile && (
                it.name.endsWith(".mp4", true) || 
                it.name.endsWith(".mkv", true) || 
                it.name.endsWith(".webm", true) || 
                it.name.endsWith(".ts", true)
            )
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun playNextVideo() {
        val files = getDownloadedFiles(context)
        if (files.size <= 1) {
            android.widget.Toast.makeText(context, "No other downloaded videos to play!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val currentIdx = files.indexOfFirst { it.absolutePath == currentVideoFile.absolutePath }
        if (currentIdx == -1) {
            currentVideoFile = files[0]
            return
        }
        
        val prefs = context.getSharedPreferences("video_progress", Context.MODE_PRIVATE)
        val direction = prefs.getString("next_play_direction", "older") ?: "older"
        
        val nextIdx = if (direction == "newer") {
            if (currentIdx == 0) files.size - 1 else currentIdx - 1
        } else {
            (currentIdx + 1) % files.size
        }
        
        currentVideoFile = files[nextIdx]
    }

    // Handle back button, orientation, and full screen system bars
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        val window = activity?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            activity?.requestedOrientation = originalOrientation
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        onBack()
    }

    // States
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Brightness, Volume, Lock, and Speed states
    var isLocked by remember { mutableStateOf(false) }
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }

    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }

    var brightnessValue by remember {
        mutableStateOf(
            activity?.window?.attributes?.screenBrightness?.let { if (it < 0) 0.5f else it } ?: 0.5f
        )
    }
    var volumeValue by remember {
        mutableStateOf(currentVolume.toFloat() / maxVolume.coerceAtLeast(1).toFloat())
    }

    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var indicatorTimerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val speedOptions = remember { listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f) }

    // Dynamic cinematic fade-in and hold speed states
    var videoFadeAlpha by remember(currentVideoFile) { mutableFloatStateOf(1f) }
    val fadeAlpha by animateFloatAsState(
        targetValue = videoFadeAlpha,
        animationSpec = tween(durationMillis = 800),
        label = "VideoFade"
    )
    var isHold2xSpeedActive by remember { mutableStateOf(false) }

    // Swipe seeking & double tap states
    var dragSeekOffset by remember { mutableLongStateOf(0L) }
    var showSeekHUD by remember { mutableStateOf(false) }
    var showLeftDoubleTapIndicator by remember { mutableStateOf(false) }
    var showRightDoubleTapIndicator by remember { mutableStateOf(false) }

    // Human readable title
    val title = remember(currentVideoFile.name) {
        currentVideoFile.nameWithoutExtension
            .replace("_", " ")
            .replace("-", " ")
            .replace("hls", "", ignoreCase = true)
            .trim()
    }

    // ExoPlayer Instance (Instantiated ONCE)
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,  // Min buffer before play (small for instant local playback)
                5000,  // Max buffer
                1000,  // Playback buffer
                1000   // Rebuffer
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    // Track previous video file to save its progress when changing
    var previousVideoFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(currentVideoFile) {
        // 1. Instantly stop and clear current playback to blackout the screen immediately
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        videoFadeAlpha = 1f // Trigger cinematic fade-in immediately

        // 2. Save progress of previous video file if exists
        previousVideoFile?.let { prevFile ->
            val curr = currentPosition
            val dur = duration
            if (curr > 2000L && dur > 0L) {
                context.getSharedPreferences("video_progress", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putLong(prevFile.absolutePath, curr)
                    .putLong("${prevFile.absolutePath}_duration", dur)
                    .apply()
            }
        }

        // 3. Update previous video file tracking to current
        previousVideoFile = currentVideoFile

        // 4. Load the new video
        val sharedPrefs = context.getSharedPreferences("video_progress", android.content.Context.MODE_PRIVATE)
        val savedPos = sharedPrefs.getLong(currentVideoFile.absolutePath, 0L)
        val totalDur = sharedPrefs.getLong("${currentVideoFile.absolutePath}_duration", 0L)

        val mediaItem = MediaItem.fromUri(currentVideoFile.absolutePath)
        exoPlayer.setMediaItem(mediaItem)

        // Seek if needed
        val shouldResume = if (totalDur > 0L && savedPos > totalDur * 0.95f) {
            false
        } else {
            savedPos > 2000L
        }

        if (shouldResume) {
            exoPlayer.seekTo(savedPos)
            currentPosition = savedPos
            android.widget.Toast.makeText(context, "Resumed playback from where you left off", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            exoPlayer.seekTo(0)
            currentPosition = 0
        }

        // Restore custom playback speed if set
        exoPlayer.setPlaybackSpeed(playbackSpeed)

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Keep track of position progress
    LaunchedEffect(isPlaying, currentVideoFile) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            
            // Persist watch progress periodically
            if (currentPosition > 2000L && duration > 0L) {
                val sharedPrefs = context.getSharedPreferences("video_progress", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putLong(currentVideoFile.absolutePath, currentPosition)
                    .putLong("${currentVideoFile.absolutePath}_duration", duration)
                    .apply()
            }
            delay(1000)
        }
    }

    // Listen to play/pause state from the player directly
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    videoFadeAlpha = 0f
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Manage Lifecycle to pause/resume/release
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Save progress on pause
                    val curr = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    if (curr > 2000L && dur > 0L) {
                        context.getSharedPreferences("video_progress", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putLong(currentVideoFile.absolutePath, curr)
                            .putLong("${currentVideoFile.absolutePath}_duration", dur)
                            .apply()
                    }
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) exoPlayer.play()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            // Save progress on dispose
            val curr = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (curr > 2000L && dur > 0L) {
                context.getSharedPreferences("video_progress", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putLong(currentVideoFile.absolutePath, curr)
                    .putLong("${currentVideoFile.absolutePath}_duration", dur)
                    .apply()
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    if (showPlayerSettingsDialog) {
        val prefs = remember { context.getSharedPreferences("video_progress", Context.MODE_PRIVATE) }
        var selectedDirection by remember { mutableStateOf(prefs.getString("next_play_direction", "older") ?: "older") }
        
        AlertDialog(
            onDismissRequest = { showPlayerSettingsDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Playback Settings",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Next Button Playback Order:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Option 1: Play Older / Downwards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedDirection = "older"
                                prefs.edit().putString("next_play_direction", "older").apply()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDirection == "older"),
                            onClick = {
                                selectedDirection = "older"
                                prefs.edit().putString("next_play_direction", "older").apply()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = Color.White.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Play Older Downloads (Aget/Nicher)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Plays towards earlier/older downloads in the list",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Option 2: Play Newer / Upwards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedDirection = "newer"
                                prefs.edit().putString("next_play_direction", "newer").apply()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDirection == "newer"),
                            onClick = {
                                selectedDirection = "newer"
                                prefs.edit().putString("next_play_direction", "newer").apply()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = Color.White.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Play Newer Downloads (Latest/Sorboses)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Plays towards recent/newest downloads in the list",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPlayerSettingsDialog = false }
                ) {
                    Text("OK", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                screenWidth = size.width.toFloat()
                screenHeight = size.height.toFloat()
            }
            .pointerInput(isLocked, playbackSpeed) {
                if (!isLocked) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            var isHoldActive = false
                            
                            // Launch a timer to trigger 2x speed after 400ms hold
                            val holdJob = scope.launch {
                                delay(400)
                                isHoldActive = true
                                isHold2xSpeedActive = true
                                exoPlayer.setPlaybackSpeed(2.0f)
                            }
                            
                            // Track pointer events until release/cancel
                            var currentEvent = awaitPointerEvent()
                            while (currentEvent.changes.any { it.id == pointerId && it.pressed }) {
                                currentEvent = awaitPointerEvent()
                            }
                            
                            // Finger is released!
                            holdJob.cancel()
                            if (isHoldActive) {
                                isHold2xSpeedActive = false
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                            }
                        }
                    }
                }
            }
            .pointerInput(isLocked, screenWidth) {
                if (isLocked) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        }
                    )
                } else {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            if (screenWidth > 0) {
                                val isRight = offset.x > (screenWidth / 2f)
                                if (isRight) {
                                    val dur = exoPlayer.duration
                                    val target = if (dur > 0) minOf(dur, exoPlayer.currentPosition + 10000L) else exoPlayer.currentPosition + 10000L
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    
                                    // Trigger right double tap ripple
                                    scope.launch {
                                        showRightDoubleTapIndicator = true
                                        delay(650)
                                        showRightDoubleTapIndicator = false
                                    }
                                } else {
                                    val target = maxOf(0L, exoPlayer.currentPosition - 10000L)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    
                                    // Trigger left double tap ripple
                                    scope.launch {
                                        showLeftDoubleTapIndicator = true
                                        delay(650)
                                        showLeftDoubleTapIndicator = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
            .pointerInput(isLocked, screenWidth, screenHeight) {
                if (!isLocked) {
                    var dragDirection = DragDirection.NONE
                    var initialPositionForSeek = 0L
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragDirection = DragDirection.NONE
                            initialPositionForSeek = exoPlayer.currentPosition
                            dragSeekOffset = 0L
                            
                            currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            volumeValue = currentVolume.toFloat() / maxVolume.coerceAtLeast(1).toFloat()
                        },
                        onDragEnd = {
                            if (dragDirection == DragDirection.HORIZONTAL) {
                                showSeekHUD = false
                            } else {
                                indicatorTimerJob?.cancel()
                                indicatorTimerJob = scope.launch {
                                    delay(1500)
                                    showVolumeIndicator = false
                                    showBrightnessIndicator = false
                                }
                            }
                            dragDirection = DragDirection.NONE
                        },
                        onDragCancel = {
                            showSeekHUD = false
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                            dragDirection = DragDirection.NONE
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (screenWidth > 0 && screenHeight > 0) {
                                // Decide direction on first drag move
                                if (dragDirection == DragDirection.NONE) {
                                    dragDirection = if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                        DragDirection.HORIZONTAL
                                    } else {
                                        DragDirection.VERTICAL
                                    }
                                }
                                
                                if (dragDirection == DragDirection.HORIZONTAL) {
                                    // Seek logic: drag across full screen width moves video by 2 minutes
                                    val sweepMultiplier = 120000f / screenWidth
                                    val deltaMs = (dragAmount.x * sweepMultiplier).toLong()
                                    dragSeekOffset += deltaMs
                                    
                                    val targetPosition = (initialPositionForSeek + dragSeekOffset).coerceIn(0L, duration.coerceAtLeast(1L))
                                    exoPlayer.seekTo(targetPosition)
                                    currentPosition = targetPosition
                                    showSeekHUD = true
                                } else if (dragDirection == DragDirection.VERTICAL) {
                                    val isRightSide = change.position.x > (screenWidth / 2f)
                                    if (isRightSide) {
                                        val delta = -dragAmount.y / screenHeight
                                        val newVolume = (volumeValue + delta).coerceIn(0f, 1f)
                                        volumeValue = newVolume
                                        val targetVol = (newVolume * maxVolume).toInt()
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                        showVolumeIndicator = true
                                        showBrightnessIndicator = false
                                    } else {
                                        val delta = -dragAmount.y / screenHeight
                                        val newBrightness = (brightnessValue + delta).coerceIn(0.01f, 1f)
                                        brightnessValue = newBrightness
                                        val lp = activity?.window?.attributes
                                        if (lp != null) {
                                            lp.screenBrightness = newBrightness
                                            activity.window.attributes = lp
                                        }
                                        showBrightnessIndicator = true
                                        showVolumeIndicator = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
    ) {
        // AndroidView rendering Media3 PlayerView
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true // Keep Screen ON during local video playback!
                    addView(PlayerView(ctx).apply {
                        useController = false
                        this.player = exoPlayer
                        this.resizeMode = resizeMode
                        this.keepScreenOn = true // Redundant KEEP SCREEN ON
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    })
                }
            },
            update = { frameLayout ->
                val playerView = frameLayout.getChildAt(0) as? PlayerView
                playerView?.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Cinematic subtle fade-in transition
        if (fadeAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = fadeAlpha))
            )
        }

        // 2X Speed Hold HUD Indicator
        if (isHold2xSpeedActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .border(1.dp, NeonCyan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "2X Speed",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "2X Speed Hold Active",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Volume & Brightness HUD Indicators
        if (showBrightnessIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp)
                    .background(Color.Black.copy(alpha = 0.75f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Brightness5,
                        contentDescription = "Brightness",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(brightnessValue * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showVolumeIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.75f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (volumeValue == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(volumeValue * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Left Double Tap Skipped Indicator Ripple overlay
        if (showLeftDoubleTapIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "-10s",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Right Double Tap Skipped Indicator Ripple overlay
        if (showRightDoubleTapIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterEnd)
                    .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "+10s",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Slide-to-seek Center HUD Progress Overlay
        if (showSeekHUD) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (dragSeekOffset >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val deltaSec = (dragSeekOffset / 1000).toInt()
                    val sign = if (deltaSec >= 0) "+" else ""
                    Text(
                        text = "$sign${deltaSec}s",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Lock / Unlock Screen State Controller Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLocked) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = {
                            isLocked = false
                            showControls = true
                        },
                        modifier = Modifier
                            .padding(start = 32.dp)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock Controls",
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    // Left-center Lock button
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 32.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(
                            onClick = {
                                isLocked = true
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Controls",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Top Header Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("offline_player_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to downloads",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Playing Offline",
                                    color = NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Playback Speed Toggle
                            IconButton(
                                onClick = {
                                    val nextIdx = (speedOptions.indexOf(playbackSpeed) + 1) % speedOptions.size
                                    playbackSpeed = speedOptions[nextIdx]
                                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Playback Speed",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            // Aspect Ratio Control
                            IconButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            // Settings Button
                            IconButton(
                                onClick = {
                                    showPlayerSettingsDialog = true
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Player Settings",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Center Action Buttons (Rewind 10s, Play/Pause, Forward 10s, Next Video)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                val target = maxOf(0L, exoPlayer.currentPosition - 10000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play/Pause Large Button
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = NeonCyan,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                val dur = exoPlayer.duration
                                val target = if (dur > 0) minOf(dur, exoPlayer.currentPosition + 10000L) else exoPlayer.currentPosition + 10000L
                                exoPlayer.seekTo(target)
                                currentPosition = target
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Next Video Button
                        IconButton(
                            onClick = {
                                playNextVideo()
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Play Next Video",
                                tint = NeonCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Bottom Timeline and Seek Slider Panel - Moved Down Closer to Bottom Edge
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, bottom = 4.dp, top = 0.dp)
                    ) {
                        // Slider
                        val sliderPosition = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                        Slider(
                            value = sliderPosition,
                            onValueChange = { percent ->
                                val target = (percent * duration).toLong()
                                exoPlayer.seekTo(target)
                                currentPosition = target
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Time Stamps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private enum class DragDirection { NONE, HORIZONTAL, VERTICAL }
