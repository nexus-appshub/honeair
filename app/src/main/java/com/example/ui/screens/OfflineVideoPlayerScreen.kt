package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Environment
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
 * Built identical to the movie and anime ExoPlayer (VideoPlayerScreen)
 * with support for portrait 16:9 view, immersive landscape fullscreen,
 * seekbar scrubbing, 10s skip controls, and multi-file playlist skipping.
 */
@OptIn(UnstableApi::class)
@Composable
fun OfflineVideoPlayerScreen(
    videoFile: File,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Playlist management for downloaded files
    var currentFile by remember { mutableStateOf(videoFile) }
    val downloadedFiles = remember(currentFile) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null && dir.exists()) {
            dir.listFiles()?.filter {
                it.isFile && (
                    it.name.endsWith(".mp4", true) ||
                    it.name.endsWith(".mkv", true) ||
                    it.name.endsWith(".webm", true) ||
                    it.name.endsWith(".ts", true)
                )
            }?.sortedByDescending { it.lastModified() } ?: listOf(currentFile)
        } else {
            listOf(currentFile)
        }
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

    // Display title cleaned from filename
    val displayTitle = remember(currentFile) {
        currentFile.nameWithoutExtension
            .replace(Regex("[_\\-\\.]+"), " ")
            .replace(Regex("(?i)(1080p|720p|480p|bluray|web-dl|x264|x265|hevc|aac)"), "")
            .trim()
            .ifBlank { currentFile.name }
    }

    // Hardware-accelerated Renderers Factory with intelligent fallback
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

    // Create ExoPlayer instance with DefaultExtractorsFactory supporting progressive MP4, TS, MKV, and WebM extractors
    // Create ExoPlayer instance with DefaultExtractorsFactory supporting progressive MP4, TS, MKV, and WebM extractors
    val exoPlayer = remember(renderersFactory) {
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )
            .setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX)

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

    var detectedMime by remember(currentFile) { mutableStateOf<String?>(null) }

    // Sniff file header bytes: if first byte is 0x47, it is MPEG-TS (even if file extension is .mp4)
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

    // Load video file into player with explicit MIME detection and local subtitle attachment
    LaunchedEffect(currentFile, detectedMime, exoPlayer) {
        playbackError = null
        if (!currentFile.exists() || currentFile.length() <= 0L) {
            playbackError = "Downloaded video file not found or empty."
            isBuffering = false
            return@LaunchedEffect
        }
        try {
            val uri = Uri.fromFile(currentFile)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (detectedMime != null) {
                mediaItemBuilder.setMimeType(detectedMime)
            }

            // Check for sidecar subtitle files (.srt, .vtt, .ass) in same folder
            val parent = currentFile.parentFile
            if (parent != null) {
                val base = currentFile.nameWithoutExtension
                val subFile = listOf(
                    File(parent, "$base.srt"),
                    File(parent, "$base.vtt"),
                    File(parent, "$base.ass")
                ).firstOrNull { it.exists() && it.length() > 0 }

                if (subFile != null) {
                    val subMime = when (subFile.extension.lowercase()) {
                        "srt" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        "vtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
                        "ass" -> androidx.media3.common.MimeTypes.TEXT_SSA
                        else -> androidx.media3.common.MimeTypes.TEXT_VTT
                    }
                    mediaItemBuilder.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                                .setMimeType(subMime)
                                .setLanguage("en")
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                        )
                    )
                }
            }

            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            playbackError = "Error initializing playback: ${e.message}"
            isBuffering = false
        }
    }

    // Position & duration tracking loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Player state listener and lifecycle cleanup
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("OfflineVideoPlayer", "Playback error on ${currentFile.name}: ${error.message}", error)
                isBuffering = false
                if (detectedMime != null) {
                    // Self-healing fallback: Try playing without forcing the sniffed MIME type
                    detectedMime = null
                } else if (!useSoftwareFallback) {
                    // Seamless automatic fallback to software decoder mode on hardware codec issue
                    useSoftwareFallback = true
                } else {
                    playbackError = "Playback error: ${error.message ?: "Format or codec issue"}"
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

    // Back button handling: exit fullscreen first if active, otherwise exit player
    BackHandler {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isFullscreen = false
        } else {
            onBack()
        }
    }

    // Window insets & orientation management
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
        // Landscape Full-Screen Immersive Mode
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showControls = !showControls }
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
                update = { pv ->
                    pv.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

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
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { exoPlayer.seekTo(it) },
                onRewind10 = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
                onForward10 = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                onPreviousVideo = {
                    if (hasPrevious) {
                        currentFile = downloadedFiles[currentFileIndex - 1]
                    }
                },
                onNextVideo = {
                    if (hasNext) {
                        currentFile = downloadedFiles[currentFileIndex + 1]
                    }
                },
                onToggleFullscreen = { isFullscreen = false },
                onToggleAspectRatio = {
                    resizeMode = when (resizeMode) {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onBack = { isFullscreen = false }
            )

            if (playbackError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Playback Error",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = playbackError ?: "Unable to decode video format",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    useSoftwareFallback = true
                                    playbackError = null
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry with SW Codec")
                            }
                            OutlinedButton(
                                onClick = { isFullscreen = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Exit Fullscreen")
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Portrait Mode with Clean Edge-to-Edge System Inset Padding
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp),
            containerColor = Color(0xFF0B0D14)
        ) { paddingValues ->
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val effectiveTopPadding = if (statusBarTop > 0.dp) statusBarTop + 4.dp else 32.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = effectiveTopPadding)
                    .navigationBarsPadding()
            ) {
                // 1. Compact 16:9 Video Player Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showControls = !showControls }
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
                        update = { pv ->
                            pv.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )

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
                        onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        onSeek = { exoPlayer.seekTo(it) },
                        onRewind10 = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
                        onForward10 = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                        onPreviousVideo = {
                            if (hasPrevious) {
                                currentFile = downloadedFiles[currentFileIndex - 1]
                            }
                        },
                        onNextVideo = {
                            if (hasNext) {
                                currentFile = downloadedFiles[currentFileIndex + 1]
                            }
                        },
                        onToggleFullscreen = { isFullscreen = true },
                        onToggleAspectRatio = {
                            resizeMode = when (resizeMode) {
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        onBack = onBack
                    )

                    if (playbackError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Playback Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = playbackError ?: "Unable to decode video format",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            useSoftwareFallback = true
                                            playbackError = null
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry SW Codec")
                                    }
                                    OutlinedButton(
                                        onClick = onBack,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("Back")
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Video Storyline, Metadata & Offline Controls Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "OFFLINE STORAGE",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        val fileExt = currentFile.extension.uppercase()
                        if (fileExt.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = fileExt,
                                    color = Color(0xFFFFB74D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        val fileSizeMB = currentFile.length() / (1024 * 1024)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = "${fileSizeMB} MB",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick action bar (Skip -10s, Play/Pause, Skip +10s, Restart)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141721))
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                        }

                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Icon(
                                if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                        }

                        IconButton(onClick = { exoPlayer.seekTo(0L) }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Restart", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Media File Info Card
                    Text(
                        text = "File Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF9800)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF141721),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("File Name", color = Color.Gray, fontSize = 12.sp)
                                Text(currentFile.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Playback Engine", color = Color.Gray, fontSize = 12.sp)
                                Text("ExoPlayer HW+ Accelerated", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Downloaded Playlist", color = Color.Gray, fontSize = 12.sp)
                                Text("${currentFileIndex + 1} of ${downloadedFiles.size} videos", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    if (downloadedFiles.size > 1) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "More Downloads",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        downloadedFiles.take(10).forEach { file ->
                            val isCurrent = file.absolutePath == currentFile.absolutePath
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) Color(0xFF1E293B) else Color(0xFF141721),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { currentFile = file }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color(0xFFFF9800) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = file.nameWithoutExtension,
                                        color = if (isCurrent) Color(0xFFFF9800) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${file.length() / (1024 * 1024)} MB",
                                        color = Color.Gray,
                                        fontSize = 11.sp
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

/**
 * Compact Player Controls Overlay identical to VideoPlayerScreen
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
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onToggleFullscreen: () -> Unit,
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
            // Top Bar: Back button, Title, Aspect Ratio & Fullscreen
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

                // Main Play/Pause button or Buffering Spinner
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
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
