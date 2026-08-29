package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun rememberShimmerBrush(
    isDark: Boolean = true
): Brush {
    val shimmerColors = if (isDark) {
        listOf(
            Color(0xFF1F1F23),
            Color(0xFF2C2C32),
            Color(0xFF1F1F23)
        )
    } else {
        listOf(
            Color(0xFFE2E2E6),
            Color(0xFFF2F2F6),
            Color(0xFFE2E2E6)
        )
    }

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

@Composable
fun ShimmerAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    isDark: Boolean = true
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    val shimmerBrush = rememberShimmerBrush(isDark = isDark)

    Box(modifier = modifier) {
        if (isLoading || isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(shimmerBrush)
            )
        }

        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .crossfade(300)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onLoading = {
                    isLoading = true
                    isError = false
                },
                onSuccess = {
                    isLoading = false
                    isError = false
                },
                onError = {
                    isLoading = false
                    isError = true
                }
            )
        }
    }
}

/**
 * Enterprise-level Preloader that pre-fetches and enqueues high-resolution movie posters
 * into the device's Memory and Disk caches completely off the Main UI Thread.
 */
@Composable
fun PreloadImages(urls: List<String>) {
    val context = LocalContext.current
    LaunchedEffect(urls) {
        if (urls.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val imageLoader = coil.Coil.imageLoader(context)
                // Preload up to the first 8 items in the list to prepare for quick scroll
                urls.take(8).forEach { url ->
                    if (!url.isNullOrBlank()) {
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }
        }
    }
}
