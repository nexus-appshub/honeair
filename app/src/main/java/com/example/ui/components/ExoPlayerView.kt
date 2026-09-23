package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.IptvChannel
import com.example.network.AiAppSmoothnessController
import com.example.network.OptimizeFrameRateEffect
import com.example.network.SmartNetworkBoosterEngine
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
fun FluidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "fluid_scale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private val BuiltInFailSafeStreams = mapOf(
    "disney" to listOf(
        "https://fl5.moveonjoy.com/DISNEY/tracks-v1a1/mono.ts.m3u8",
        "http://fl3.moveonjoy.com/DISNEY_CHANNEL/index.m3u8",
        "http://103.182.170.32:8888/play/a01y",
        "http://66.102.120.18:8000/play/a078/index.m3u8"
    ),
    "geographic" to listOf(
        "http://66.102.120.18:8000/play/a01f/index.m3u8",
        "http://149.71.34.166:8002/play/a013/index.m3u8",
        "http://stream02.vnet.am/NatGeoWild/tracks-v1a2/mono.m3u8",
        "http://40.160.24.53/NAT_GEO/index.m3u8"
    ),
    "nat geo" to listOf(
        "http://66.102.120.18:8000/play/a01f/index.m3u8",
        "http://149.71.34.166:8002/play/a013/index.m3u8",
        "http://stream02.vnet.am/NatGeoWild/tracks-v1a2/mono.m3u8",
        "http://40.160.24.53/NAT_GEO/index.m3u8"
    ),
    "discovery" to listOf(
        "http://66.102.120.18:8000/play/a01e/index.m3u8",
        "http://149.71.34.166:8002/play/a011/index.m3u8"
    ),
    "hbo" to listOf(
        "http://66.102.120.18:8000/play/a076/index.m3u8",
        "http://149.71.34.166:8002/play/a01c/index.m3u8"
    ),
    "star sports" to listOf(
        "http://103.204.145.242:8000/starsports1/index.m3u8"
    ),
    "sony sports" to listOf(
        "http://103.204.145.242:8000/sonysports1/index.m3u8"
    )
)

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(isMiniPlayer: Boolean = false, onMiniPlayerToggle: () -> Unit = {}, 
    streamUrl: String,
    modifier: Modifier = Modifier,
    channelName: String = "Live Feed",
    isFullScreen: Boolean = false,
    isInPipMode: Boolean = false,
    fallbackUrl: String? = null,
    onFullScreenToggle: () -> Unit = {},
    onPlaybackError: (String) -> Unit = {},
    onPlaybackSuccess: () -> Unit = {},
    onAutoNext: () -> Unit = {},
    onAutoPrev: () -> Unit = {},
    externalIsPlaying: Boolean = true,
    onPlayPauseToggle: (Boolean) -> Unit = {},
    onBack: (() -> Unit)? = null,
    channels: List<IptvChannel> = emptyList(),
    onSelectChannel: ((IptvChannel) -> Unit)? = null,
    isBatterySaverMode: Boolean = false,
    customHeaders: Map<String, String> = emptyMap()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var retryCount by remember(currentUrl) { mutableIntStateOf(0) }
    var currentAlternateIndex by remember(streamUrl) { mutableIntStateOf(0) }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember(currentUrl) { mutableStateOf(externalIsPlaying) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    val sidebarListState = rememberLazyListState()
    LaunchedEffect(showChannelDrawer, channelName, channels) {
        if (showChannelDrawer && channels.isNotEmpty()) {
            val index = channels.indexOfFirst { it.name == channelName }
            if (index >= 0) {
                sidebarListState.animateScrollToItem(index)
            }
        }
    }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Gesture Notification banner
    var gestureNotification by remember { mutableStateOf<String?>(null) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    var showQualitySelectionDialog by remember { mutableStateOf(false) }
    var isCheckingQualities by remember { mutableStateOf(false) }
    var fetchedQualities by remember { mutableStateOf<List<com.example.download.MediaDownloader.HlsQuality>>(emptyList()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification permission enabled. Download progress will show in status bar.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            if (!isDraggingSlider) {
                currentPosition = player.currentPosition
                duration = maxOf(0L, player.duration)
            }
            delay(300)
        }
    }

    LaunchedEffect(gestureNotification) {
        if (gestureNotification != null) {
            delay(1800)
            gestureNotification = null
        }
    }

    LaunchedEffect(externalIsPlaying) {
        exoPlayer?.let { player ->
            if (externalIsPlaying != player.playWhenReady) {
                player.playWhenReady = externalIsPlaying
                isPlaying = externalIsPlaying
            }
        }
    }

    // Start Smart AI Network Booster Engine
    LaunchedEffect(Unit) {
        SmartNetworkBoosterEngine.startEngine(context)
        AiAppSmoothnessController.boostThreadPriority()
    }

    OptimizeFrameRateEffect()

    val batterySaverActive = remember(isBatterySaverMode) {
        if (isBatterySaverMode) {
            true
        } else {
            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val isSystemPowerSave = powerManager?.isPowerSaveMode == true
            var isLowBattery = false
            try {
                val batteryStatus: android.content.Intent? = context.registerReceiver(
                    null,
                    android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                )
                val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    val batteryPct = level * 100f / scale.toFloat()
                    if (batteryPct <= 20f) {
                        val status: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == android.os.BatteryManager.BATTERY_STATUS_FULL
                        if (!isCharging) {
                            isLowBattery = true
                        }
                    }
                }
            } catch (_: Exception) {}
            isSystemPowerSave || isLowBattery
        }
    }

    // Initialize ExoPlayer with Network Booster Engine
    val isLiveStream = remember(currentUrl, channelName, channels) {
        channels.isNotEmpty() ||
        currentUrl.contains("live", ignoreCase = true) ||
        currentUrl.contains("iptv", ignoreCase = true) ||
        channelName.contains("TV", ignoreCase = true) ||
        channelName.contains("Live", ignoreCase = true)
    }

    LaunchedEffect(currentUrl, isBatterySaverMode, customHeaders) {
        errorMessage = null
        retryCount = 0
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
        
        val loadControl = if (batterySaverActive) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000,
                    30000,
                    1000,
                    2000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            SmartNetworkBoosterEngine.createDynamicLoadControl(userBufferIndex, isLiveStream = isLiveStream)
        }

        val renderersFactory = SmartNetworkBoosterEngine.createRenderersFactory(
            context = context,
            decoderMode = userDecoderIndex,
            isHardwareAccelerated = isHwAccel && !batterySaverActive
        )

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            if (batterySaverActive) {
                setParameters(
                    buildUponParameters()
                        .setMaxVideoSize(854, 480) // 480p max resolution
                        .setMaxVideoFrameRate(24)  // 24fps max frame rate
                )
            }
        }

        val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(300_000L) // Crucial for low-latency areas: starts with 300kbps estimate to avoid HD buffer stalling on start
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setWakeMode(C.WAKE_MODE_NETWORK) // Keep network sockets alive
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
            .apply {
                val mediaItemBuilder = MediaItem.Builder().setUri(currentUrl)
                val urlLower = currentUrl.lowercase()
                if (urlLower.contains(".m3u8") || urlLower.contains("m3u8") || urlLower.contains("hls")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                } else if (urlLower.contains(".mpd") || urlLower.contains("dash")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                } else if (urlLower.contains(".mp4") || urlLower.contains("mp4")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MP4)
                }

                // Configure resilient Live Target Offset only for live streams
                if (isLiveStream) {
                    val liveConfig = MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.15f) // Automatically speeds up up to 15% to catch up to live edge if delayed by jitter
                        .setMinPlaybackSpeed(0.85f) // Automatically slows down to 85% to recover buffer smoothly rather than freezing
                        .setTargetOffsetMs(6000)    // 6 seconds targets a robust buffer cushion
                        .setMinOffsetMs(2000)
                        .setMaxOffsetMs(40000)
                        .build()
                    mediaItemBuilder.setLiveConfiguration(liveConfig)
                }

                val mediaItem = mediaItemBuilder.build()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = externalIsPlaying
            }

        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
                onPlayPauseToggle(playWhenReady)
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    retryCount = 0
                    errorMessage = null
                    onPlaybackSuccess()
                } else if (state == Player.STATE_ENDED) {
                    if (isLiveStream) {
                        scope.launch {
                            delay(1000)
                            try {
                                player.seekToDefaultPosition()
                                player.prepare()
                                player.play()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                retryCount++
                scope.launch {
                    val backoff = if (retryCount <= 3) 1500L else 3000L
                    delay(backoff)
                    try {
                        if (isLiveStream) {
                            player.seekToDefaultPosition()
                        } else {
                            val cur = player.currentPosition
                            if (cur > 0) player.seekTo(cur)
                        }
                        player.prepare()
                        player.play()
                    } catch (_: Exception) {
                        // Dynamic multi-source alternate fallback recovery on error
                        val alternates = mutableListOf<String>()
                        if (!fallbackUrl.isNullOrBlank() && fallbackUrl != streamUrl) {
                            alternates.add(fallbackUrl)
                        }
                        val nameLower = channelName.lowercase()
                        for ((key, urls) in BuiltInFailSafeStreams) {
                            if (nameLower.contains(key)) {
                                urls.forEach { u ->
                                    if (u != streamUrl && !alternates.contains(u)) {
                                        alternates.add(u)
                                    }
                                }
                            }
                        }
                        
                        if (alternates.isNotEmpty() && retryCount > 2) {
                            val nextIndex = currentAlternateIndex % alternates.size
                            currentUrl = alternates[nextIndex]
                            currentAlternateIndex++
                            retryCount = 0
                            gestureNotification = "Error Recovery: Backup Server Active"
                            Toast.makeText(context, "Recovering stream using Backup Server...", Toast.LENGTH_SHORT).show()
                        } else if (!fallbackUrl.isNullOrBlank() && fallbackUrl != currentUrl && retryCount > 5) {
                            currentUrl = fallbackUrl
                            retryCount = 0
                            gestureNotification = "Switched to Backup Stream"
                        }
                    }
                }
            }
        })

        exoPlayer = player
    }

    // Continuous Live Stream Stall/Freeze Watchdog: Auto-detects and seamlessly recovers if live stream freezes
    LaunchedEffect(exoPlayer, currentUrl, isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        var bufferingSeconds = 0
        var watchdogRestartCount = 0

        while (true) {
            delay(2000)
            if (player.playWhenReady && !isDraggingSlider) {
                val state = player.playbackState
                val isCurrentlyPlaying = player.isPlaying

                // 1. Recover from actual stuck buffering (>8s) without interrupting healthy playing state
                if (state == Player.STATE_BUFFERING && !isCurrentlyPlaying) {
                    bufferingSeconds += 2
                    if (bufferingSeconds >= 8) {
                        bufferingSeconds = 0
                        watchdogRestartCount++
                        
                        val alternates = mutableListOf<String>()
                        if (!fallbackUrl.isNullOrBlank() && fallbackUrl != streamUrl) {
                            alternates.add(fallbackUrl)
                        }
                        val nameLower = channelName.lowercase()
                        for ((key, urls) in BuiltInFailSafeStreams) {
                            if (nameLower.contains(key)) {
                                urls.forEach { u ->
                                    if (u != streamUrl && !alternates.contains(u)) {
                                        alternates.add(u)
                                    }
                                }
                            }
                        }
                        
                        if (alternates.isNotEmpty() && watchdogRestartCount >= 2) {
                            val nextIndex = currentAlternateIndex % alternates.size
                            val targetUrl = alternates[nextIndex]
                            currentAlternateIndex++
                            watchdogRestartCount = 0
                            withContext(Dispatchers.Main) {
                                currentUrl = targetUrl
                                gestureNotification = "Auto-switched to Live Backup Server"
                                Toast.makeText(context, "Buffering too long, switching to Backup Server...", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            try {
                                player.seekToDefaultPosition()
                                player.prepare()
                                player.play()
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    bufferingSeconds = 0
                }

                // 2. Recover from stuck idle state
                if (state == Player.STATE_IDLE && !isCurrentlyPlaying) {
                    try {
                        player.prepare()
                        player.play()
                    } catch (_: Exception) {}
                }
            } else {
                bufferingSeconds = 0
            }
        }
    }

    // Keep Wifi and CPU active during live streaming to prevent OS power management disconnects
    DisposableEffect(context) {
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val wifiLock = wifiManager?.createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
            } else {
                @Suppress("DEPRECATION")
                android.net.wifi.WifiManager.WIFI_MODE_FULL
            },
            "HomeAirTV:LiveStreamWifiLock"
        )?.apply {
            setReferenceCounted(false)
            try { acquire() } catch (_: Exception) {}
        }

        val powerManager = context.applicationContext.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "HomeAirTV:LiveStreamWakeLock"
        )?.apply {
            setReferenceCounted(false)
            try { acquire(12 * 60 * 60 * 1000L) } catch (_: Exception) {}
        }

        onDispose {
            try { if (wifiLock?.isHeld == true) wifiLock.release() } catch (_: Exception) {}
            try { if (wakeLock?.isHeld == true) wakeLock.release() } catch (_: Exception) {}
        }
    }

    // Keep Screen On permanently while ExoPlayer is active
    val activity = context as? android.app.Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Handle activity lifecycle safely
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isInPipMode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!isInPipMode) { exoPlayer?.playWhenReady = false }
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer?.playWhenReady = true
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying, isControlsLocked) {
        if (showControls && isPlaying && !isControlsLocked) {
            delay(4500)
            showControls = false
        }
    }

    // Touch Drag Gestures tracking (Vertical: Channel Prev/Next, Horizontal Right: Quick Channel Drawer)
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("stream_player_box")
            .clickable {
                if (showChannelDrawer) {
                    showChannelDrawer = false
                } else {
                    showControls = !showControls
                }
            }
            .pointerInput(isFullScreen, isControlsLocked, showChannelDrawer) {
                if (!isControlsLocked && !showChannelDrawer) {
                    detectDragGestures(
                        onDragStart = {
                            totalDragY = 0f
                            totalDragX = 0f
                        },
                        onDragEnd = {
                            if (isFullScreen) {
                                // Swipe Right -> Open Channel Drawer
                                if (totalDragX > 140f && abs(totalDragY) < 100f) {
                                    showChannelDrawer = true
                                    gestureNotification = "📺 Channel Drawer Opened"
                                }
                                // Swipe Up -> Next Channel
                                else if (totalDragY < -120f && abs(totalDragX) < 100f) {
                                    gestureNotification = "▲ Next Channel"
                                    onAutoNext()
                                }
                                // Swipe Down -> Prev Channel
                                else if (totalDragY > 120f && abs(totalDragX) < 100f) {
                                    gestureNotification = "▼ Previous Channel"
                                    onAutoPrev()
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragY += dragAmount.y
                            totalDragX += dragAmount.x
                        }
                    )
                }
            }
    ) {
        if (errorMessage == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        keepScreenOn = true
                        useController = false
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.keepScreenOn = true
                    view.player = exoPlayer
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay 1: Network Booster & Buffering Loader
        if (playbackState == Player.STATE_BUFFERING && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        // Overlay 2: Playback Error Screen
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularOff,
                        contentDescription = null,
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Stream Connection Issue",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Low network or channel source timeout",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            retryCount = 0
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Retry Stream", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Overlay 3: Gesture Notification Badge
        AnimatedVisibility(
            visible = gestureNotification != null && errorMessage == null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, NeonCyan)
            ) {
                Text(
                    text = gestureNotification ?: "",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = NeonCyan
                )
            }
        }

        // Overlay 4: Locked Touch Overlay Indicator
        if (isControlsLocked && errorMessage == null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isFullScreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                FluidIconButton(
                    onClick = {
                        isControlsLocked = false
                        showControls = true
                        gestureNotification = "🔓 Player Unlocked"
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .border(1.5.dp, NeonCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock Controls",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Overlay 5: Modern Glassmorphic Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isControlsLocked && errorMessage == null && !isInPipMode,
            enter = fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = 0.92f, animationSpec = tween(280)),
            exit = fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.92f, animationSpec = tween(220))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Toolbar - Aligned closer to the top border in portrait mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .then(if (isFullScreen) Modifier.statusBarsPadding() else Modifier)
                        .padding(top = if (isFullScreen) 16.dp else 8.dp, start = if (isFullScreen) 16.dp else 12.dp, end = if (isFullScreen) 16.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button, Channel name & HD badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onBack != null) {
                            FluidIconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // HD Badge & Channel Info - Compact
                        Column {
                            if (batterySaverActive) {
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFFE11D48).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Eco,
                                        contentDescription = "Battery Saver",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "BATTERY SAVER (480P • 24FPS)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (playbackState == Player.STATE_READY) Color.Green else Color.Yellow)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (playbackState == Player.STATE_READY) "ULTRA HD" else "BOOSTER CONNECT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Right: Lock, Channel Drawer Button, Download, Aspect Ratio & Fullscreen
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // Quick Channel Switcher Drawer Icon
                        if (isFullScreen && channels.isNotEmpty()) {
                            FluidIconButton(
                                onClick = { showChannelDrawer = !showChannelDrawer },
                                modifier = Modifier
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatListNumbered,
                                    contentDescription = "Channels List",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Player Lock Button
                        FluidIconButton(
                            onClick = {
                                isControlsLocked = true
                                showControls = false
                                gestureNotification = "🔒 Controls Locked"
                            },
                            modifier = Modifier
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Controls",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Floating Player PiP Button
                        FluidIconButton(
                            onClick = {
                                onMiniPlayerToggle()
                            },
                            modifier = Modifier
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Floating Player Mode",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }



                        // Aspect Ratio Switcher
                        FluidIconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                gestureNotification = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit Screen"
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom Screen"
                                    else -> "Fill Screen"
                                }
                            },
                            modifier = Modifier
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Fullscreen Toggle
                        FluidIconButton(
                            onClick = onFullScreenToggle,
                            modifier = Modifier
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Center Controls (Spacious & Clean Layout)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (isFullScreen) 32.dp else 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val btnSize = if (isFullScreen) 52.dp else 32.dp
                    val iconSize = if (isFullScreen) 28.dp else 16.dp
                    val playSize = if (isFullScreen) 68.dp else 44.dp
                    val playIconSize = if (isFullScreen) 40.dp else 22.dp

                    if (isFullScreen) {
                        FluidIconButton(
                            onClick = onAutoPrev,
                            modifier = Modifier
                                .size(btnSize)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Channel",
                                tint = Color.White,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }

                    // 10s Backward Seek Button
                    FluidIconButton(
                        onClick = {
                            exoPlayer?.let { player ->
                                val target = maxOf(0L, player.currentPosition - 10000)
                                player.seekTo(target)
                                currentPosition = target
                            }
                        },
                        modifier = Modifier
                            .size(btnSize)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    FluidIconButton(
                        onClick = {
                            exoPlayer?.let { player ->
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        },
                        modifier = Modifier
                            .size(playSize)
                            .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                            .border(1.5.dp, NeonCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = NeonCyan,
                            modifier = Modifier.size(playIconSize)
                        )
                    }

                    // 10s Forward Seek Button
                    FluidIconButton(
                        onClick = {
                            exoPlayer?.let { player ->
                                val dur = player.duration
                                val target = if (dur > 0) minOf(dur, player.currentPosition + 10000) else player.currentPosition + 10000
                                player.seekTo(target)
                                currentPosition = target
                            }
                        },
                        modifier = Modifier
                            .size(btnSize)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    if (isFullScreen) {
                        FluidIconButton(
                            onClick = onAutoNext,
                            modifier = Modifier
                                .size(btnSize)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Channel",
                                tint = Color.White,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }

                // Bottom Timeline & Seek Bar - Aligned closer to the bottom border in portrait mode
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(if (isFullScreen) Modifier.navigationBarsPadding() else Modifier)
                        .padding(horizontal = if (isFullScreen) 24.dp else 12.dp)
                        .padding(bottom = if (isFullScreen) 40.dp else 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (duration > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMillis(if (isDraggingSlider) sliderPosition.toLong() else currentPosition),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatMillis(duration),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = if (isDraggingSlider) sliderPosition else currentPosition.toFloat(),
                            onValueChange = { newValue ->
                                isDraggingSlider = true
                                sliderPosition = newValue
                            },
                            onValueChangeFinished = {
                                exoPlayer?.seekTo(sliderPosition.toLong())
                                isDraggingSlider = false
                            },
                            valueRange = 0f..maxOf(1f, duration.toFloat()),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                // Bottom Hint (Swipe Gesture Help)
                if (isFullScreen) {
                    Text(
                        text = "Swipe ↑↓ for Channels • Swipe → for Channel List",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Quick Channel Switcher Sidebar (Drawer Overlay on Swipe Right / Icon Click in Fullscreen)
        AnimatedVisibility(
            visible = showChannelDrawer && isFullScreen && channels.isNotEmpty(),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp),
                color = Color.Black.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quick Channels (${channels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        IconButton(onClick = { showChannelDrawer = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        state = sidebarListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(channels) { ch ->
                            val isSelected = ch.name == channelName
                            val hasError = isSelected && errorMessage != null
                            val highlightColor = when {
                                hasError -> Color(0xFFFF0000)
                                isSelected -> NeonCyan
                                else -> Color.Transparent
                            }
                            Surface(
                                onClick = {
                                    showChannelDrawer = false
                                    onSelectChannel?.invoke(ch)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (hasError) Color(0xFFFF0000).copy(alpha = 0.25f) else if (isSelected) NeonCyan.copy(alpha = 0.25f) else DeepSlate.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, highlightColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) NeonCyan else Color.Transparent)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Small Channel Logo Image / Fallback Icon
                                    if (!ch.logo.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.logo,
                                            contentDescription = ch.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(NeonCyan.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (isSelected) NeonCyan else Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (hasError) Color(0xFFFF0000) else if (isSelected) NeonCyan else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQualitySelectionDialog) {
        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        AlertDialog(
            onDismissRequest = { showQualitySelectionDialog = false },
            containerColor = DeepSlate,
            title = {
                Text(
                    text = "Select Download Quality",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Source: $channelName",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isCheckingQualities) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    } else if (fetchedQualities.isNotEmpty()) {
                        Text(
                            text = "Multiple video qualities detected. Please select one:",
                            color = TextPrimary.copy(alpha = 0.85f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        fetchedQualities.forEach { quality ->
                            Button(
                                onClick = {
                                    val sanitizedTitle = channelName.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
                                    val finalFileName = "${sanitizedTitle}_${quality.resolution}.mp4"
                                    com.example.download.MediaDownloader.downloadFile(
                                        context = context,
                                        url = quality.url,
                                        fileName = finalFileName,
                                        coroutineScope = scope
                                    )
                                    showQualitySelectionDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color(0xFFF1F5F9),
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = quality.resolution,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Single / Default Quality
                        Text(
                            text = "Standard video stream detected. Would you like to download in default quality?",
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val sanitizedTitle = channelName.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
                                val finalFileName = "${sanitizedTitle}.mp4"
                                com.example.download.MediaDownloader.downloadFile(
                                    context = context,
                                    url = currentUrl,
                                    fileName = finalFileName,
                                    coroutineScope = scope
                                )
                                showQualitySelectionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Download Default/Best Quality",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showQualitySelectionDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
