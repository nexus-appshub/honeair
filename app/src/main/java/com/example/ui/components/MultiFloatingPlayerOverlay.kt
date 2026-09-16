package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.model.FloatingPlayerInstance
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SpaceBlack
import com.example.ui.viewmodel.StreamViewModel
import kotlin.math.roundToInt

/**
 * MultiFloatingPlayerOverlay manages multiple concurrent draggable PIP / Floating player windows
 * (supporting up to 2 to 6 concurrent streams simultaneously) on top of the entire application.
 */
@Composable
fun MultiFloatingPlayerOverlay(
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier
) {
    val floatingPlayers by viewModel.activeFloatingPlayers.collectAsState()
    val maxLimit by viewModel.maxFloatingPlayers.collectAsState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    if (floatingPlayers.isEmpty()) return

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Render each active floating player instance
        floatingPlayers.forEachIndexed { index, instance ->
            DraggableFloatingPlayerWindow(
                instance = instance,
                index = index,
                totalCount = floatingPlayers.size,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                onClose = { viewModel.removeFloatingPlayer(instance.id) },
                onToggleMute = { viewModel.toggleFloatingPlayerMute(instance.id) },
                onTogglePlay = { viewModel.toggleFloatingPlayerPlay(instance.id) },
                onExpand = { viewModel.expandFloatingPlayerToMain(instance) },
                viewModel = viewModel
            )
        }

        // Multi-Player Floating HUD Bar (Visible when 2 or more players are active)
        if (floatingPlayers.size >= 2) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                color = Color(0xFF161B26).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF00E676), CircleShape)
                    )
                    Text(
                        text = "Multi-View Active: ${floatingPlayers.size}/$maxLimit",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { viewModel.clearAllFloatingPlayers() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LayersClear,
                            contentDescription = "Close All Floating Players",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Floating Player Window with drag gesture, media controls, audio toggle, and ExoPlayer rendering.
 */
@OptIn(UnstableApi::class)
@Composable
private fun DraggableFloatingPlayerWindow(
    instance: FloatingPlayerInstance,
    index: Int,
    totalCount: Int,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onClose: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    viewModel: StreamViewModel
) {
    val context = LocalContext.current

    // Scale and Size logic
    var scale by remember(instance.id) { mutableFloatStateOf(1f) }
    val baseWidthDp = 220.dp
    val baseHeightDp = 135.dp
    val playerWidthDp = baseWidthDp * scale
    val playerHeightDp = baseHeightDp * scale

    val density = LocalDensity.current
    val playerWidthPx = with(density) { playerWidthDp.toPx() }
    val playerHeightPx = with(density) { playerHeightDp.toPx() }

    // Stagger initial positions nicely across the screen
    val defaultStartX = (index % 2) * (with(density) { baseWidthDp.toPx() } * 0.95f) + 30f
    val defaultStartY = 160f + (index / 2) * (with(density) { baseHeightDp.toPx() } + 40f)

    var offsetX by remember(instance.id) { mutableFloatStateOf(defaultStartX) }
    var offsetY by remember(instance.id) { mutableFloatStateOf(defaultStartY) }

    // ExoPlayer lifecycle management for this floating instance
    val exoPlayer = remember(instance.id) {
        val sharedPrefs = context.getSharedPreferences("stream_app_prefs", android.content.Context.MODE_PRIVATE)
        val userBufferIndex = sharedPrefs.getInt("setting_buffer_index", 1)
        val userDecoderIndex = sharedPrefs.getInt("setting_decoder_index", 0)
        val isHwAccel = sharedPrefs.getBoolean("setting_hardware_accel", true)

        val renderersFactory = com.example.network.SmartNetworkBoosterEngine.createRenderersFactory(
            context = context,
            decoderMode = userDecoderIndex,
            isHardwareAccelerated = isHwAccel
        )
        val loadControl = com.example.network.SmartNetworkBoosterEngine.createDynamicLoadControl(userBufferIndex, isLiveStream = true)

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = instance.isPlaying
                volume = if (instance.isMuted) 0f else 1f
            }
    }

    // Update volume and play state dynamically
    LaunchedEffect(instance.isMuted) {
        exoPlayer.volume = if (instance.isMuted) 0f else 1f
    }
    LaunchedEffect(instance.isPlaying) {
        exoPlayer.playWhenReady = instance.isPlaying
    }

    // Set media source
    LaunchedEffect(instance.streamUrl, instance.channel?.url) {
        val targetUrl = instance.streamUrl ?: instance.channel?.url
        if (!targetUrl.isNullOrBlank()) {
            try {
                val mergedHeaders = mutableMapOf<String, String>()
                instance.channel?.headers?.let { mergedHeaders.putAll(it) }
                mergedHeaders.putAll(instance.headers)

                val httpDataSourceFactory = com.example.network.SmartNetworkBoosterEngine.createBoostedHttpDataSourceFactory(
                    customHeaders = mergedHeaders,
                    url = targetUrl
                )
                val mediaSourceFactory = com.example.network.SmartNetworkBoosterEngine.createOptimizedMediaSourceFactory(context, httpDataSourceFactory)
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(targetUrl))
                    .build()

                val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(instance.id) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    offsetX.roundToInt().coerceIn(0, (screenWidthPx - playerWidthPx).toInt().coerceAtLeast(0)),
                    offsetY.roundToInt().coerceIn(0, (screenHeightPx - playerHeightPx).toInt().coerceAtLeast(0))
                )
            }
            .size(width = playerWidthDp, height = playerHeightDp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.5.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .pointerInput(instance.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 2.5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        // Player Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Control Overlay Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(NeonPurple, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "PIP ${index + 1}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = instance.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Mute / Unmute
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (instance.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (instance.isMuted) "Unmute" else "Mute",
                            tint = if (instance.isMuted) Color(0xFFFF5252) else NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Expand to Main Screen
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand to Main",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Close Window
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // Bottom status / Play-Pause pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp),
                onClick = onTogglePlay
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (instance.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (instance.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = if (instance.isChannel) "LIVE" else instance.subtitle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (instance.isChannel) Color(0xFFFF5252) else NeonCyan
                    )
                }
            }
        }
    }
}
