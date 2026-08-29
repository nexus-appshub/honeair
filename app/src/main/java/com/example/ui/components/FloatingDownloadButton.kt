package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.download.DownloadInfo
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import kotlin.math.roundToInt
import kotlin.math.hypot

@Composable
fun FloatingDownloadButton(
    downloads: List<DownloadInfo>,
    onClick: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (downloads.isEmpty()) return

    val totalProgress = if (downloads.isNotEmpty()) {
        downloads.map { it.progress }.average().toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = (totalProgress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "floating_dl_progress"
    )

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val dropThresholdPx = remember(density) { with(density) { 35.dp.toPx() } }

    Surface(
        shape = CircleShape,
        color = DeepSlate.copy(alpha = 0.92f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.5.dp, if (isDragging) Color(0xFFFF6B00) else NeonCyan.copy(alpha = 0.8f)),
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(52.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        // Check if dragged towards Air Tab (bottom center area: positive Y or towards center left)
                        if (offsetY > dropThresholdPx || (offsetY > 15.dp.toPx() && offsetX < -30.dp.toPx())) {
                            onDismiss()
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .clickable {
                if (hypot(offsetX, offsetY) < 15f) {
                    onClick()
                } else {
                    onClick()
                }
            }
            .testTag("floating_download_btn")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Circular progress border
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val strokeWidth = 2.5.dp.toPx()
                drawArc(
                    color = Color.Gray.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = if (isDragging) Color(0xFFFF6B00) else NeonCyan,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Active Downloads",
                    tint = if (isDragging) Color(0xFFFF6B00) else NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${totalProgress.toInt()}%",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
