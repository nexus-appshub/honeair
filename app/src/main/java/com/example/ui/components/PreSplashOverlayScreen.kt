package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.viewmodel.AppControlConfig
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PreSplashOverlayScreen(
    config: AppControlConfig,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val context = LocalContext.current
    val isVideo = remember(config.preSplashMediaUrl, config.preSplashMediaType) {
        config.preSplashMediaType.equals("video", ignoreCase = true) ||
        config.preSplashMediaUrl.lowercase().contains(".mp4") ||
        config.preSplashMediaUrl.lowercase().contains(".m3u8")
    }

    var isMuted by remember { mutableStateOf(false) }
    var remainingSeconds by remember(config.preSplashSkipSeconds) {
        val seconds = config.preSplashSkipSeconds
        mutableIntStateOf(if (seconds <= 0) 5 else seconds)
    }

    val canSkip = remainingSeconds <= 0

    // Countdown timer for Skip button
    LaunchedEffect(config.preSplashSkipSeconds) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
    }

    // Full Screen Overlay Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("pre_splash_overlay_screen")
    ) {
        // Background layer: blurred image if image ad, or elegant charcoal dark pattern for videos
        if (config.preSplashMediaUrl.isNotBlank()) {
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF161618),
                                    Color(0xFF09090B)
                                )
                            )
                        )
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(config.preSplashMediaUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                    )
                }
            }
        }

        if (isVideo && config.preSplashMediaUrl.isNotBlank()) {
            // Media3 Fullscreen Video Player
            var isPlayerReady by remember { mutableStateOf(false) }
            val exoPlayer = remember(config.preSplashMediaUrl) {
                ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.fromUri(Uri.parse(config.preSplashMediaUrl))
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = if (isMuted) 0f else 1f
                    playWhenReady = true
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                isPlayerReady = true
                            }
                        }
                    })
                }
            }

            LaunchedEffect(isMuted) {
                exoPlayer.volume = if (isMuted) 0f else 1f
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    exoPlayer.stop()
                    exoPlayer.release()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (config.preSplashCtaUrl.isNotBlank()) {
                            onOpenLink(config.preSplashCtaUrl)
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        val view = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.exo_player_texture_view, null) as PlayerView
                        view.apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (!isPlayerReady) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF6B00),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        } else if (config.preSplashMediaUrl.isNotBlank()) {
            // Fullscreen Image Poster - Fit Content Scale to prevent cropping
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (config.preSplashCtaUrl.isNotBlank()) {
                            onOpenLink(config.preSplashCtaUrl)
                        }
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(config.preSplashMediaUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Pre-Splash Promo Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Default elegant background placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2C1B4D), Color(0xFF0F0B1E)),
                            radius = 2000f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF6B00),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Loading Premium Content...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Top Gradient for Controls Readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom Gradient for CTA Readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // TOP CONTROLS BAR: Sound Toggle & Skip Countdown Button (No Sponsor Badge / No Cross Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.End, // Align skip/sound controls to the right
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right Action Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Audio mute/unmute button (only for video)
                if (isVideo && config.preSplashMediaUrl.isNotBlank()) {
                    Surface(
                        onClick = { isMuted = !isMuted },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Skip / Countdown Button (Removed Cross Icon, Just Clean Text Box)
                if (canSkip) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFF6B00),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("pre_splash_skip_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Skip",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Skip in ${remainingSeconds}s",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM ACTION BUTTON (CTA) (Promo Text Card Removed)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config.preSplashCtaUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = { onOpenLink(config.preSplashCtaUrl) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.5.dp, Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pre_splash_cta_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = config.preSplashCtaText.ifBlank { "Learn More" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
