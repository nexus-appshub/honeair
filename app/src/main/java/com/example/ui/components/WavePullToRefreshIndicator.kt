package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WavePullToRefreshIndicator(
    state: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = state.distanceFraction.coerceIn(0f, 1f)
    if (progress <= 0f && !isRefreshing) return

    val infiniteTransition = rememberInfiniteTransition(label = "WavePhaseTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    val waveHeight by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "WaveHeight"
    )

    Box(
        modifier = modifier
            .padding(top = 10.dp)
            .height(48.dp)
            .width((140 + waveHeight * 40).dp)
            .shadow(elevation = (8 * waveHeight).dp, shape = RoundedCornerShape(24.dp), spotColor = NeonCyan)
            .clip(RoundedCornerShape(24.dp))
            .background(DeepSlate.copy(alpha = 0.95f))
            .border(BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f * waveHeight)), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Fluid Wave Canvas Background
        Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            // Primary Wave Path (Neon Cyan)
            val wavePath1 = Path().apply {
                moveTo(0f, height)
                for (x in 0..width.toInt() step 4) {
                    val angle = (x.toFloat() / width) * (2 * Math.PI).toFloat() * 2f + wavePhase
                    val y = midY + (sin(angle.toDouble()) * (6f * waveHeight)).toFloat()
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                close()
            }
            drawPath(
                path = wavePath1,
                brush = Brush.verticalGradient(
                    colors = listOf(NeonCyan.copy(alpha = 0.40f), NeonCyan.copy(alpha = 0.08f))
                )
            )

            // Secondary Wave Path (Neon Magenta)
            val wavePath2 = Path().apply {
                moveTo(0f, height)
                for (x in 0..width.toInt() step 4) {
                    val angle = (x.toFloat() / width) * (2 * Math.PI).toFloat() * 2.5f - wavePhase * 1.2f
                    val y = midY + (cos(angle.toDouble()) * (7f * waveHeight)).toFloat() + 2f
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                close()
            }
            drawPath(
                path = wavePath2,
                brush = Brush.verticalGradient(
                    colors = listOf(NeonMagenta.copy(alpha = 0.30f), NeonMagenta.copy(alpha = 0.04f))
                )
            )
        }

        // Center Content: Glowing Icon + Text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Waves,
                contentDescription = "Wave Refreshing",
                tint = NeonCyan,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        rotationZ = if (isRefreshing) wavePhase * (180f / Math.PI.toFloat()) else progress * 180f
                    }
            )
            Text(
                text = if (isRefreshing) "Refreshing..." else if (progress >= 1f) "Release to refresh" else "Pull to refresh",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}
