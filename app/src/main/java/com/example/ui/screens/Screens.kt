// UTF-8 encoding marker: বাংলা HomeAirTV IPTV App Screens v1.0.0 ✨
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import com.example.notifications.LocalNotificationManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.*


import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.database.AdminLogEntity
import com.example.data.model.IptvChannel
import com.example.data.model.IptvPlaylist
import com.example.data.model.MediaItem
import com.example.ui.components.ExoPlayerView
import com.example.ui.components.ShimmerAsyncImage
import com.example.ui.components.PreloadImages
import com.example.ui.components.CinemetaWebView
import com.example.ui.components.MasterAnimeBrowserModal
import com.example.subscription.SubscriptionManager
import com.example.ui.components.OfficialGoogleLogo
import com.example.ui.viewmodel.StreamViewModel
import com.example.ui.viewmodel.TelemetryStats
import com.example.ui.viewmodel.UiState
import com.example.ui.theme.*
import com.example.update.AppUpdateManager
import androidx.compose.ui.zIndex
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.compose.ui.draw.alpha
import com.example.update.UpdateInfo
import com.example.update.UpdateCheckResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun DigitalClockWidget() {
    var currentTime by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = NeonCyan.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        border = BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(
                colors = listOf(NeonCyan, NeonPurple, NeonMagenta)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.12f),
                            LightAccent.copy(alpha = 0.3f),
                            NeonPurple.copy(alpha = 0.12f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Top Live Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepSlate)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "LIVE IPTV STREAMING ENGINE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        ),
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = timeFormat.format(currentTime),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                    color = NeonPurple,
                    letterSpacing = 2.sp
                )
                Text(
                    text = dateFormat.format(currentTime),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatPill(icon = Icons.Default.Speed, label = "Ultra Fast", value = "4K / HD")
                    HorizontalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = BorderColor)
                    StatPill(icon = Icons.Default.Public, label = "Nodes", value = "150+ Live")
                    HorizontalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = BorderColor)
                    StatPill(icon = Icons.Default.Security, label = "Status", value = "Secure")
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = NeonCyan,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = TextSecondary
            )
        }
    }
}

@Composable
fun DeveloperNoteFooter(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val url = "https://www.xubilaswebdevcorp.shop"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                try {
                    uriHandler.openUri(url)
                } catch (_: Exception) {}
            }
            .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = NeonCyan.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                colors = listOf(NeonCyan, NeonPurple, NeonMagenta)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.1f),
                            LightAccent.copy(alpha = 0.3f),
                            NeonPurple.copy(alpha = 0.1f)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NeonPurple, NeonCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Developer",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "DEVELOPER CREDIT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = "Xubilas Web Dev Corp",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = NeonCyan
                    )
                }
            }
        }
    }
}


// ==========================================
// 1. AUTH / SIGN IN SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current


    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var pendingDeviceAccounts by remember { mutableStateOf<List<String>?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack),
        contentAlignment = Alignment.Center
    ) {
        // Neon Background Glow Orbs
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(y = (-150).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NeonPurple.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = 100.dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Futuristic App Logo Mark
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DeepSlate)
                    .border(1.5.dp, NeonCyan, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Logo",
                    tint = NeonCyan,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "HOME AIR TV",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Next-Generation Global Stream Space",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // Sign In Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(24.dp),
                border = BoxBorder(BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Access Broadcast Control",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    val context = LocalContext.current


                    // Standard Google Login Button
                    Button(
                        onClick = {
                            viewModel.signInWithGoogle(
                                context = context,
                                onSuccess = {
                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_signup_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OfficialGoogleLogo(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Sign in with Google",
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        pendingDeviceAccounts?.let { accounts ->
            RealGoogleAccountsDialog(
                accounts = accounts,
                onDismiss = { pendingDeviceAccounts = null },
                onAccountSelected = { selectedEmail ->
                    viewModel.login(selectedEmail, selectedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }, "")
                    pendingDeviceAccounts = null
                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}




@Composable
fun AnimatedHomeAirLogo(
    modifier: Modifier = Modifier,
    clickTrigger: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")

    // Rotate 360 degrees occasionally (every 6 seconds: 4.5s still, 1.5s spin)
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at 4500
                360f at 5800 using androidx.compose.animation.core.FastOutSlowInEasing
                360f at 6000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_rotation"
    )

    // Smooth rotation spin triggered on screen click
    val clickRotation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(clickTrigger) {
        if (clickTrigger > 0) {
            clickRotation.animateTo(
                targetValue = clickRotation.value + 360f,
                animationSpec = tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
    }

    val totalRotation = baseRotation + clickRotation.value

    // Flowing light phase along the border
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_flow"
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Offset cx slightly to the right to visually center the triangle inside the circle
        val visualCx = cx + (size.width * 0.04f)
        val radius = size.width * 0.22f // sizing the triangle nicely (scaled down slightly for better elegance)

        // Helper function to rotate a point (px, py) around (cx, cy) by an angle in degrees
        fun rotatePoint(px: Float, py: Float, angleDegrees: Float): androidx.compose.ui.geometry.Offset {
            val rad = Math.toRadians(angleDegrees.toDouble())
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            val dx = px - cx
            val dy = py - cy
            val rx = cx + dx * cos - dy * sin
            val ry = cy + dx * sin + dy * cos
            return androidx.compose.ui.geometry.Offset(rx, ry)
        }

        // Unrotated equilateral triangle pointing right
        val p1X = visualCx + radius
        val p1Y = cy
        val p2X = visualCx - radius * 0.5f
        val p2Y = cy - radius * 0.866f
        val p3X = visualCx - radius * 0.5f
        val p3Y = cy + radius * 0.866f

        // Apply total rotation to the points
        val rotP1 = rotatePoint(p1X, p1Y, totalRotation)
        val rotP2 = rotatePoint(p2X, p2Y, totalRotation)
        val rotP3 = rotatePoint(p3X, p3Y, totalRotation)

        // Path for the triangle
        val trianglePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(rotP1.x, rotP1.y)
            lineTo(rotP2.x, rotP2.y)
            lineTo(rotP3.x, rotP3.y)
            close()
        }

        // 1. Draw solid white inner triangle
        drawPath(
            path = trianglePath,
            color = Color.White
        )

        // 2. Compute approximate perimeter of triangle to scale dash path effect
        val side = radius * 1.732f
        val perimeter = side * 3f

        // 3. Draw a glowing/flowing border stroke along each side
        val dashLength = perimeter * 0.35f
        val gapLength = perimeter - dashLength
        val phaseOffset = (flowPhase / 360f) * perimeter

        // Base thin semi-transparent border so the path outline is visible
        drawPath(
            path = trianglePath,
            color = Color.White.copy(alpha = 0.3f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        // Bright animated flowing highlight stroke
        drawPath(
            path = trianglePath,
            color = Color(0xFFFFF176), // Beautiful glowing gold/yellow
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.5.dp.toPx(),
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    intervals = floatArrayOf(dashLength, gapLength),
                    phase = -phaseOffset
                )
            )
        )
    }
}

@Composable
fun HomeAirTvBrandingHeader(
    selectedPlaylistName: String? = null,
    modifier: Modifier = Modifier,
    clickTrigger: Int = 0
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val headerTextColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF6B00).copy(alpha = 0.15f))
                .border(1.5.dp, Color(0xFFFF6B00).copy(alpha = 0.4f), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B00)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedHomeAirLogo(
                    modifier = Modifier.fillMaxSize(),
                    clickTrigger = clickTrigger
                )
            }
        }

        if (selectedPlaylistName != null) {
            Text(
                text = selectedPlaylistName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    letterSpacing = 0.sp
                ),
                color = headerTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = "HOME ",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = headerTextColor
                )
                Text(
                    text = "AIR",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        letterSpacing = 1.2.sp,
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFFB74D),
                                Color(0xFFFF7A00),
                                Color(0xFFFF3D00)
                            )
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun DashboardCategoryIconButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            gradient[0].copy(alpha = 0.25f),
                            gradient[1].copy(alpha = 0.12f)
                        )
                    )
                )
                .shadow(8.dp, CircleShape, spotColor = gradient[0].copy(alpha = 0.4f))
                .border(
                    BorderStroke(
                        1.8.dp,
                        Brush.linearGradient(gradient)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            ),
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HubCategoryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

data class HomeCategoryItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

// ==========================================
// 2. HOME SCREEN (Advanced Dashboard & IPTV Hub)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMediaTab: (String) -> Unit = {},
    onNavigateToAirTab: () -> Unit = {},
    onBackPress: () -> Unit = {},
    isHeaderVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlistsState by viewModel.playlistsState.collectAsState()
    val channelsState by viewModel.channelsState.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()

    val playlistSearchQuery by viewModel.playlistSearchQuery.collectAsState()
    val channelSearchQuery by viewModel.channelSearchQuery.collectAsState()

    val isInPipMode by viewModel.isInPipMode.collectAsState()

    val filteredPlaylists by viewModel.filteredPlaylists.collectAsState()
    val filteredChannels by viewModel.filteredChannels.collectAsState()

    val userProfile by viewModel.userProfile.collectAsState()
    val updateInfoState by viewModel.updateInfoState.collectAsState()
    val selectedAudioIndex by viewModel.audioIndex.collectAsState()
    val maxFloatingPlayers by viewModel.maxFloatingPlayers.collectAsState()
    var showSignInSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showWatchHistorySheet by remember { mutableStateOf(false) }
    var showWebVersionView by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showCopyrightSheet by remember { mutableStateOf(false) }
    var showFloatingPlayerLimitSheet by remember { mutableStateOf(false) }
    var showMasterAnimeBrowser by remember { mutableStateOf(false) }
    var showCineStreamBrowser by remember { mutableStateOf(false) }
    var activeWebPlayer by remember { mutableStateOf<WebPlayerState?>(null) }
    var selectedItemForDetail by remember { mutableStateOf<MediaItem?>(null) }
    var activePreviewFeedItemId by remember { mutableStateOf<String?>(null) }
    var showNotificationPopup by remember { mutableStateOf(false) }
    var hasNewNotification by remember { mutableStateOf(false) }
    val mediaWatchHistory by viewModel.mediaWatchHistory.collectAsState()
    val appControlConfig by viewModel.appControlConfig.collectAsState()


    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.tabReselectEvent.collect { tabIndex ->
            if (tabIndex == 0 || tabIndex == 1 || tabIndex == 3) {
                activeWebPlayer = null
            }
        }
    }

    var isLiveTvViewMode by remember { mutableStateOf(false) }
    var isSportsViewMode by remember { mutableStateOf(false) }
    var showLiveTvBottomSheet by remember { mutableStateOf(false) }
    var isIptvSearchActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.tabReselectEvent.collect { tabIndex ->
            if (tabIndex == 0) {
                isLiveTvViewMode = false
                isSportsViewMode = false
                isIptvSearchActive = false
            }
        }
    }

    val mediaState by viewModel.mediaState.collectAsState()
    val latestReleases by viewModel.latestReleases.collectAsState()
    val discoverFeedItems by viewModel.discoverFeedItems.collectAsState()
    val isDiscoverFeedRefreshing by viewModel.isDiscoverFeedRefreshing.collectAsState()
    val realMediaList = remember(mediaState) {
        ((mediaState as? UiState.Success<*>)?.data as? List<*>)?.filterIsInstance<MediaItem>() ?: emptyList()
    }
    val effectiveDiscoverFeedList = if (discoverFeedItems.isNotEmpty()) discoverFeedItems else realMediaList

    PreloadImages(urls = realMediaList.map { it.imageUrl })

    val bannerMediaList = remember(realMediaList, latestReleases) {
        if (latestReleases.isNotEmpty()) {
            latestReleases.take(8)
        } else if (realMediaList.isNotEmpty()) {
            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            realMediaList.shuffled(kotlin.random.Random(today)).take(6)
        } else emptyList()
    }

    var bannerIndex by remember { mutableIntStateOf(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 1000,
        pageCount = { if (bannerMediaList.isNotEmpty()) Int.MAX_VALUE else 1 }
    )
    LaunchedEffect(bannerMediaList.size) {
        if (bannerMediaList.isNotEmpty()) {
            while (true) {
                kotlinx.coroutines.delay(4000)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage + 1,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }
    LaunchedEffect(pagerState.currentPage, bannerMediaList.size) {
        if (bannerMediaList.isNotEmpty()) {
            bannerIndex = pagerState.currentPage % bannerMediaList.size
        }
    }

    val currentBannerItem = remember(bannerMediaList, bannerIndex) {
        if (bannerMediaList.isNotEmpty()) bannerMediaList.getOrNull(bannerIndex) else null
    }

    var selectedCategoryFilter by remember { mutableStateOf("All") }
    val categories = remember { listOf("All", "Fixed Channels", "Sports", "News", "Movies", "Music", "Kids", "Global") }

    val displayPlaylists = remember(filteredPlaylists, selectedCategoryFilter) {
        val filteredNonUpdate = filteredPlaylists.filter { playlist ->
            !playlist.name.contains("UPDATE CHANNELS", ignoreCase = true) &&
            !playlist.name.contains("Sport TV", ignoreCase = true) &&
            !playlist.name.contains("Fixed Channels", ignoreCase = true)
        }
        if (selectedCategoryFilter == "All") {
            filteredNonUpdate
        } else {
            val matched = filteredNonUpdate.filter { playlist ->
                playlist.name.contains(selectedCategoryFilter, ignoreCase = true) ||
                playlist.group.contains(selectedCategoryFilter, ignoreCase = true)
            }
            val recommended = filteredNonUpdate.filter { it.group == "Recommended" }
            (recommended + matched).distinct()
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    LaunchedEffect(playlistSearchQuery, channelSearchQuery, selectedCategoryFilter, selectedPlaylist) {
        try {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        } catch (e: Exception) {
            // Ignore scroll errors
        }
    }

    var isSearchBarVisible by remember { mutableStateOf(true) }
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }

    val currentScrollIndex by remember(selectedPlaylist) {
        derivedStateOf {
            if (selectedPlaylist == null) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
        }
    }
    val currentScrollOffset by remember(selectedPlaylist) {
        derivedStateOf {
            if (selectedPlaylist == null) gridState.firstVisibleItemScrollOffset else listState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(currentScrollIndex, currentScrollOffset) {
        if (currentScrollIndex > prevIndex || (currentScrollIndex == prevIndex && currentScrollOffset > prevOffset + 15)) {
            if (currentScrollIndex > 0 || currentScrollOffset > 30) {
                isSearchBarVisible = false
            }
        } else if (currentScrollIndex < prevIndex || (currentScrollIndex == prevIndex && currentScrollOffset < prevOffset - 15)) {
            isSearchBarVisible = true
        }
        prevIndex = currentScrollIndex
        prevOffset = currentScrollOffset
    }

    BackHandler(enabled = true) {
        if (isIptvSearchActive) {
            isIptvSearchActive = false
            viewModel.setPlaylistSearchQuery("")
            viewModel.setChannelSearchQuery("")
        } else if (activeWebPlayer != null) {
            activeWebPlayer = null
        } else if (selectedItemForDetail != null) {
            selectedItemForDetail = null
        } else if (selectedPlaylist != null) {
            viewModel.clearSelectedPlaylist()
        } else if (isSportsViewMode) {
            isSportsViewMode = false
        } else if (isLiveTvViewMode) {
            isLiveTvViewMode = false
        } else {
            onBackPress()
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val homeBgColor = if (isDark) SpaceBlack else Color(0xFFF5F5F7)
    val homeCardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val homeTextColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val homeSubTextColor = if (isDark) Color(0xFFA1A1A6) else Color(0xFF8E8E93)
    val homeIconTint = if (isDark) Color.White else Color(0xFF1C1C1E)
    val homeCategoryBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFEF)
    val homeCategoryIconTint = if (isDark) Color.White else Color(0xFF3A3A3C)
    val homeBorderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    var screenClickCount by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            screenClickCount++
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(homeBgColor)
                .statusBarsPadding()
        ) {
        var isUniversalSearchActive by remember { mutableStateOf(false) }
        var universalSearchQuery by remember { mutableStateOf("") }

        // Main App Header Row (Branding on Left; Search, Bell & Sign In on Right)
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + expandVertically(expandFrom = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
        ) {
            AnimatedContent(
                targetState = isUniversalSearchActive,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + expandHorizontally())
                        .togetherWith(fadeOut(animationSpec = tween(180)) + shrinkHorizontally())
                },
                label = "UniversalSearchHeaderAnimation"
            ) { searchActive ->
                if (searchActive) {
                    // Expanded Search Bar connected directly to Movie & Anime Database with Fuzzy Matching
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = homeCardBg,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isUniversalSearchActive = false
                                    universalSearchQuery = ""
                                    viewModel.setMediaSearchQuery("")
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = universalSearchQuery,
                                onValueChange = { 
                                    universalSearchQuery = it
                                    viewModel.setMediaSearchQuery(it)
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = homeTextColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(NeonCyan),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (universalSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Search movies, anime, series, live TV...",
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = homeSubTextColor,
                                                fontSize = 13.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (universalSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { 
                                        universalSearchQuery = ""
                                        viewModel.setMediaSearchQuery("")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = homeSubTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            HomeAirTvBrandingHeader(
                                selectedPlaylistName = if (isSportsViewMode) "Sports Zone" else selectedPlaylist?.name,
                                modifier = Modifier,
                                clickTrigger = screenClickCount
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val isIptvView = isLiveTvViewMode || selectedPlaylist != null

                            if (isSportsViewMode) {
                                IconButton(
                                    onClick = { viewModel.fetchSportsData() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = homeIconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

        // Universal Fuzzy Search Results Overlay
        AnimatedVisibility(
            visible = isUniversalSearchActive && universalSearchQuery.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val fuzzyResults = remember(universalSearchQuery, realMediaList) {
                viewModel.fuzzySearchMedia(universalSearchQuery, realMediaList)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = homeCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Matches found for " + universalSearchQuery,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (fuzzyResults.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No titles found. Try checking the spelling.", color = homeSubTextColor, fontSize = 13.sp)
                        }
                    } else {
                        val searchScrollState = rememberLazyListState()
                        LaunchedEffect(universalSearchQuery) {
                            if (universalSearchQuery.isNotEmpty()) {
                                try {
                                    searchScrollState.scrollToItem(0)
                                } catch (e: Exception) {
                                    // Ignore scroll errors
                                }
                            }
                        }
                        LazyColumn(
                            state = searchScrollState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(fuzzyResults, key = { "search_" + it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedItemForDetail = item
                                            isUniversalSearchActive = false
                                        }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp, 56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DeepSlate)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = homeTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.category}  ${item.year.ifEmpty { "HD" }}  \u2605 ${item.rating}",
                                            fontSize = 11.sp,
                                            color = homeSubTextColor
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.playMediaItem(item, 1, 1)
                                            isUniversalSearchActive = false
                                            onNavigateToPlayer()
                                        },
                                        modifier = Modifier.size(32.dp).background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

                            if (isIptvView) {
                                IconButton(
                                    onClick = {
                                        isIptvSearchActive = !isIptvSearchActive
                                        if (!isIptvSearchActive) {
                                            viewModel.setPlaylistSearchQuery("")
                                            viewModel.setChannelSearchQuery("")
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isIptvSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = homeIconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {


                                // Notification Bell Button with Red Badge
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = {
                                    showNotificationPopup = true
                                    hasNewNotification = false
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = homeIconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            val activeDownloads by com.example.download.MediaDownloader.activeDownloads.collectAsState()
                            if (hasNewNotification || activeDownloads.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp, end = 2.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    if (activeDownloads.isNotEmpty()) NeonCyan else Color(0xFFFF3B30),
                                                    if (activeDownloads.isNotEmpty()) Color(0xFF00B4D8) else Color(0xFFFF6B6B)
                                                )
                                            )
                                        )
                                        .border(1.5.dp, if (isDark) Color(0xFF1C1C1E) else Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (activeDownloads.isNotEmpty()) activeDownloads.size.toString() else "1",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                                                    if (showNotificationPopup) {
                                androidx.compose.material3.ModalBottomSheet(
                                    onDismissRequest = { showNotificationPopup = false },
                                    containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                                    scrimColor = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Brush.linearGradient(listOf(NeonCyan, NeonPurple))),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                }
                                                Text(
                                                    text = "Notification Center",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 18.sp,
                                                    color = homeTextColor
                                                )
                                            }
                                            IconButton(onClick = { showNotificationPopup = false }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Close", tint = homeSubTextColor, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        HorizontalDivider(color = homeBorderColor)

                                        val activeDownloadsList = activeDownloads
                                        if (activeDownloadsList.isNotEmpty()) {
                                            Text("Active Downloads", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan)
                                            for (download in activeDownloadsList) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                                            CircularProgressIndicator(
                                                                progress = { download.progress / 100f },
                                                                color = NeonCyan,
                                                                strokeWidth = 3.dp,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                            Text("${download.progress}%", fontSize = 10.sp, color = homeTextColor, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(download.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = homeTextColor)
                                                            Text(
                                                                text = when {
                                                                    download.isCancelled -> "Cancelled"
                                                                    download.isPaused -> "Paused  ${download.getFormattedStatus()}"
                                                                    else -> download.getFormattedStatus()
                                                                },
                                                                fontSize = 11.sp,
                                                                color = homeSubTextColor
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        val downloadContext = androidx.compose.ui.platform.LocalContext.current
                                                        IconButton(
                                                            onClick = { com.example.download.MediaDownloader.togglePauseDownload(downloadContext, download.id) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (download.isPaused) androidx.compose.material.icons.Icons.Default.PlayArrow else androidx.compose.material.icons.Icons.Default.Pause,
                                                                contentDescription = if (download.isPaused) "Resume" else "Pause",
                                                                tint = NeonCyan,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { com.example.download.MediaDownloader.cancelDownload(download.id) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // AppsHub Update Notification (Only if updateInfoState != null)
                                        updateInfoState?.let { updateInfo ->
                                            Text("System Alerts", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonPurple)
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    showNotificationPopup = false
                                                }
                                            ) {
                                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonPurple.copy(alpha=0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("App Update Available (v${updateInfo.latestVersionName})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = homeTextColor)
                                                        Text(updateInfo.releaseNotes.ifBlank { "A new version is available from Apps Hub." }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = homeSubTextColor)
                                                    }
                                                }
                                            }
                                        }

                                        Text("General", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonMagenta)
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth().clickable { showNotificationPopup = false }
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonMagenta.copy(alpha=0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(24.dp))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Welcome to Home Air TV", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = homeTextColor)
                                                    Text("Enjoy seamless streaming & downloading.", fontSize = 11.sp, color = homeSubTextColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(2.dp))

                    if (!isIptvView) {
                        // Universal Search Icon Button (Connected to Movies/Anime Hub Database)
                        IconButton(
                            onClick = { isUniversalSearchActive = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Universal Search",
                                tint = homeIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

    val profile = userProfile
                    if (!isLiveTvViewMode && selectedPlaylist == null) {
                        // "Sign In" Button Pill
                        Surface(
                            onClick = {
                                if (profile != null) {
                                    showProfileSheet = true
                                } else {
                                    showSignInSheet = true
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = homeCardBg,
                            border = BorderStroke(1.dp, homeBorderColor),
                            shadowElevation = 2.dp,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF6B00)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profile?.avatarUrl != null && profile.avatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = profile.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Sign In",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                if (profile == null) {
                                    Text(
                                        text = com.example.ui.theme.AppTranslation.getString("sign_in", selectedAudioIndex),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = homeTextColor
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

        // Universal Fuzzy Search Results Overlay
        AnimatedVisibility(
            visible = isUniversalSearchActive && universalSearchQuery.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val fuzzyResults = remember(universalSearchQuery, realMediaList) {
                viewModel.fuzzySearchMedia(universalSearchQuery, realMediaList)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = homeCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Matches found for " + universalSearchQuery,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (fuzzyResults.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No titles found. Try checking the spelling.", color = homeSubTextColor, fontSize = 13.sp)
                        }
                    } else {
                        val searchScrollState = rememberLazyListState()
                        LaunchedEffect(universalSearchQuery) {
                            if (universalSearchQuery.isNotEmpty()) {
                                try {
                                    searchScrollState.scrollToItem(0)
                                } catch (e: Exception) {
                                    // Ignore scroll errors
                                }
                            }
                        }
                        LazyColumn(
                            state = searchScrollState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(fuzzyResults, key = { "search_" + it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedItemForDetail = item
                                            isUniversalSearchActive = false
                                        }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp, 56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DeepSlate)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = homeTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.category}  ${item.year.ifEmpty { "HD" }}  \u2605 ${item.rating}",
                                            fontSize = 11.sp,
                                            color = homeSubTextColor
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.playMediaItem(item, 1, 1)
                                            isUniversalSearchActive = false
                                            onNavigateToPlayer()
                                        },
                                        modifier = Modifier.size(32.dp).background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val isIptvView = isLiveTvViewMode || selectedPlaylist != null

        AnimatedVisibility(
            visible = isIptvView && isIptvSearchActive,
            enter = expandVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeIn(
                animationSpec = tween(durationMillis = 350)
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeOut(
                animationSpec = tween(durationMillis = 250)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                SearchBar(
                    query = if (selectedPlaylist == null) playlistSearchQuery else channelSearchQuery,
                    onQueryChange = {
                        if (selectedPlaylist == null) viewModel.setPlaylistSearchQuery(it)
                        else viewModel.setChannelSearchQuery(it)
                    },
                    placeholder = "", // Clean and empty as requested
                    modifier = Modifier.fillMaxWidth()
                )

                // Live suggestions inside Playlist preview mode
                if (selectedPlaylist != null && channelSearchQuery.length >= 3) {
                    val suggestions = filteredChannels.take(4)
                    if (suggestions.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = homeCardBg),
                            border = BorderStroke(1.dp, Color(0xFFFF6B00)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .zIndex(10f)
                        ) {
                            Column {
                                suggestions.forEach { sugChannel ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setChannelSearchQuery("")
                                                isIptvSearchActive = false
                                                viewModel.setActiveChannel(sugChannel)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFFFF6B00), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = sugChannel.name,
                                            color = homeTextColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // VIEW 1: ADVANCED MAIN DASHBOARD HUB
                if (isSportsViewMode) {
                    SportsHubScreen(
                        viewModel = viewModel,
                        onNavigateToPlayer = onNavigateToPlayer,
                        onBack = { isSportsViewMode = false }
                    )
                } else if (!isLiveTvViewMode && selectedPlaylist == null) {
            var isHomeRefreshing by remember { mutableStateOf(false) }
            val homeScope = rememberCoroutineScope()
            @OptIn(ExperimentalMaterial3Api::class)
            val homePullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

            @OptIn(ExperimentalMaterial3Api::class)
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isHomeRefreshing,
                onRefresh = {
                    homeScope.launch {
                        isHomeRefreshing = true
                        viewModel.loadMediaItems(forceRefresh = true)
                        kotlinx.coroutines.delay(600)
                        isHomeRefreshing = false
                    }
                },
                state = homePullState,
                indicator = {
                    com.example.ui.components.WavePullToRefreshIndicator(
                        state = homePullState,
                        isRefreshing = isHomeRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                // Admin Panel Sponsored Ad Banner
                item {
                    com.example.ui.components.AdminAdBannerCard(
                        config = appControlConfig,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                // 1. Featured Watch Anything Hero Banner Carousel
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(178.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Side: Text and CTA Watch Button
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1.1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "\u2605 ${currentBannerItem?.rating ?: "8.5"} Rating",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 18.sp
                                            ),
                                            color = if (isDark) Color.White else Color(0xFF1C1C1E)
                                        )
                                    }

                                    Text(
                                        text = currentBannerItem?.title ?: "Masters of the Universe",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF6B00),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            currentBannerItem?.let { item ->
                                                viewModel.playMediaItem(item)
                                                onNavigateToPlayer()
                                            } ?: run {
                                                onNavigateToMediaTab("Movies")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Watch Now",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = com.example.ui.theme.AppTranslation.getString("watch_now", selectedAudioIndex),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))                                 // Right Side: Classic Horizontal Camera Film Strip with Top & Bottom Perforations
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .fillMaxHeight()
                                        .clickable {
                                            currentBannerItem?.let { item ->
                                                viewModel.playMediaItem(item)
                                                onNavigateToPlayer()
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Top & Bottom Camera Film Sprocket Perforations
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(7.dp),
                                                horizontalArrangement = Arrangement.SpaceAround,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                repeat(8) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 6.dp, height = 4.dp)
                                                            .clip(RoundedCornerShape(1.dp))
                                                            .background(if (isDark) Color(0xFF0A0A0C) else Color(0xFFD1D1D6))
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(7.dp),
                                                horizontalArrangement = Arrangement.SpaceAround,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                repeat(8) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 6.dp, height = 4.dp)
                                                            .clip(RoundedCornerShape(1.dp))
                                                            .background(if (isDark) Color(0xFF0A0A0C) else Color(0xFFD1D1D6))
                                                    )
                                                }
                                            }
                                        }

                                        // Camera Film Reel Carousel
                                        androidx.compose.foundation.pager.HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp, vertical = 7.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            pageSpacing = 0.dp,
                                            userScrollEnabled = true
                                        ) { page ->
                                            val actualIndex = if (bannerMediaList.isNotEmpty()) page % bannerMediaList.size else 0
                                            val bannerItem = bannerMediaList.getOrNull(actualIndex) ?: currentBannerItem

                                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                            val progress = pageOffset.coerceIn(0f, 1f)

                                            // 1. Color desaturation: 1f (Full Color on Right Grid) -> 0f (Grey / Grayscale on Left Grid)
                                            val saturation = (1f - progress).coerceIn(0f, 1f)
                                            val colorMatrix = if (saturation < 0.98f) {
                                                androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation) }
                                            } else null

                                            // 2. Wavy Camera Roll Strip Curve Transformation for Left Grid
                                            val scale = androidx.compose.ui.util.lerp(1f, 0.82f, progress)
                                            val alpha = androidx.compose.ui.util.lerp(1f, 0.72f, progress)
                                            val rotationZ = androidx.compose.ui.util.lerp(0f, -8f, progress)
                                            val translationY = androidx.compose.ui.util.lerp(0f, 12f, progress)

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF1C1C1E),
                                                border = BorderStroke(1.2.dp, Color(0xFFFF6B00)),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (bannerItem != null && bannerItem.imageUrl.isNotEmpty()) {
                                                        AsyncImage(
                                                            model = bannerItem.imageUrl,
                                                            contentDescription = bannerItem.title,
                                                            contentScale = ContentScale.Crop,
                                                            colorFilter = colorMatrix?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(it) },
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        listOf(
                                                                            Color.Transparent,
                                                                            Color.Black.copy(alpha = 0.6f)
                                                                        )
                                                                    )
                                                                )
                                                        )

                                                        // Play Icon Badge on Active Right Frame
                                                        if (progress < 0.3f) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFFF6B00).copy(alpha = 0.9f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PlayArrow,
                                                                    contentDescription = "Play",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C2E)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Tv,
                                                                contentDescription = null,
                                                                tint = Color.White.copy(alpha = 0.9f),
                                                                modifier = Modifier.size(24.dp)
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // Banner Carousel Indicator Dots
                        val totalBannerItems = if (bannerMediaList.isNotEmpty()) bannerMediaList.size else 1
                        val coroutineScope = rememberCoroutineScope()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(totalBannerItems) { idx ->
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (idx == bannerIndex) 16.dp else 6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (idx == bannerIndex) Color(0xFFFF6B00) else Color(0xFFD1D1D6))
                                        .clickable {
                                            coroutineScope.launch {
                                                val diff = idx - (pagerState.currentPage % totalBannerItems)
                                                pagerState.animateScrollToPage(pagerState.currentPage + diff)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // 2. EXPLORE CATEGORIES
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.example.ui.theme.AppTranslation.getString("explore_categories", selectedAudioIndex),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = homeTextColor
                            )

                            val viewAllInteractionSource = remember { MutableInteractionSource() }
                            val isViewAllPressed by viewAllInteractionSource.collectIsPressedAsState()
                            val viewAllScale by animateFloatAsState(
                                targetValue = if (isViewAllPressed) 0.90f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "viewAllScale"
                            )
                            Text(
                                text = com.example.ui.theme.AppTranslation.getString("view_all", selectedAudioIndex),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isViewAllPressed) Color(0xFF00E676) else Color(0xFFFF6B00),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = viewAllScale
                                        scaleY = viewAllScale
                                    }
                                    .clickable(
                                        interactionSource = viewAllInteractionSource,
                                        indication = null
                                    ) {
                                        onNavigateToMediaTab("All")
                                        viewModel.setShowCategoriesGrid(true)
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Horizontally Scrollable Category Row
                        val exploreCategoriesList = remember(selectedAudioIndex) {
                            listOf(
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("live_tv", selectedAudioIndex), Icons.Outlined.Tv) { showLiveTvBottomSheet = true },
                                HomeCategoryItem("Latest", Icons.Default.AutoAwesome) { onNavigateToMediaTab("Latest") },
                                HomeCategoryItem("All", Icons.Default.GridOn) { onNavigateToMediaTab("All") },
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("movies", selectedAudioIndex), Icons.Outlined.Movie) { onNavigateToMediaTab("Movies") },
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("tv_shows", selectedAudioIndex), Icons.Outlined.LiveTv) { onNavigateToMediaTab("Series & TV Shows") },
                                HomeCategoryItem("Anime", Icons.Outlined.LiveTv) { onNavigateToMediaTab("Anime") },
                                HomeCategoryItem("Bangla", Icons.Outlined.MovieFilter) { onNavigateToMediaTab("Bangla") },
                                HomeCategoryItem("Hindi Cinema", Icons.Outlined.Movie) { onNavigateToMediaTab("Hindi Cinema") },
                                HomeCategoryItem("Hindi Series", Icons.Outlined.Tv) { onNavigateToMediaTab("Hindi Series") },
                                HomeCategoryItem("Hindi Dubbed", Icons.Default.RecordVoiceOver) { onNavigateToMediaTab("Hindi Dubbed") },
                                HomeCategoryItem("Hindi K-Drama", Icons.Default.FavoriteBorder) { onNavigateToMediaTab("Hindi K-Drama") },
                                HomeCategoryItem("Anime Series", Icons.Outlined.SmartDisplay) { onNavigateToMediaTab("Anime Series") },
                                HomeCategoryItem("Anime Movies", Icons.Outlined.Theaters) { onNavigateToMediaTab("Anime Movies") },
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("k_drama", selectedAudioIndex), Icons.Outlined.OndemandVideo) { onNavigateToMediaTab("K-Dramas") },
                                HomeCategoryItem("Action", Icons.Default.Whatshot) { onNavigateToMediaTab("Action") },
                                HomeCategoryItem("Sci-Fi", Icons.Default.Public) { onNavigateToMediaTab("Sci-Fi") },
                                HomeCategoryItem("Sports", Icons.Outlined.SportsSoccer) { onNavigateToMediaTab("Sports") },
                                HomeCategoryItem("News", Icons.Outlined.Newspaper) { onNavigateToMediaTab("News") },
                                HomeCategoryItem("Kids", Icons.Outlined.ChildCare) { onNavigateToMediaTab("Kids") },
                                HomeCategoryItem("Music", Icons.Outlined.MusicNote) { onNavigateToMediaTab("Music") }
                            )
                        }

                        val categoryListState = rememberLazyListState()
                        val categoryScope = rememberCoroutineScope()
                        val totalCatPages = remember(exploreCategoriesList.size) {
                            (exploreCategoriesList.size + 1) / 2
                        }
                        val currentCatPage by remember(totalCatPages) {
                            derivedStateOf {
                                val firstIdx = categoryListState.firstVisibleItemIndex
                                (firstIdx / 2).coerceIn(0, totalCatPages - 1)
                            }
                        }

                        LazyRow(
                            state = categoryListState,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(exploreCategoriesList, key = { it.title }) { catItem ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.88f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "catItemScale"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) { catItem.onClick() }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPressed) {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp) // Larger than the icon box
                                                    .background(
                                                        brush = Brush.radialGradient(
                                                            colors = listOf(
                                                                Color(0xFF00E676).copy(alpha = 0.6f),
                                                                Color.Transparent
                                                            )
                                                        ),
                                                        shape = CircleShape
                                                    )
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .background(homeCategoryBg)
                                                .border(
                                                    BorderStroke(
                                                        width = 1.dp,
                                                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                                                    ),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = catItem.icon,
                                                contentDescription = catItem.title,
                                                tint = if (isPressed) Color(0xFF00E676) else homeCategoryIconTint,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = catItem.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPressed) Color(0xFF00E676) else homeTextColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic Workable Category Dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            repeat(totalCatPages) { page ->
                                val isActive = page == currentCatPage
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (isActive) 16.dp else 6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isActive) Color(0xFFFF6B00) else if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            categoryScope.launch {
                                                categoryListState.animateScrollToItem((page * 2).coerceAtMost(exploreCategoriesList.size - 1))
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // Continue Watching Section (Placed above Latest with sleek progress bar)
                if (mediaWatchHistory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ContinueWatchingRowSection(
                                title = "Continue Watching",
                                items = mediaWatchHistory.take(15),
                                viewModel = viewModel,
                                onSeeAllClick = { onNavigateToMediaTab("All") },
                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item }
                            )
                        }
                    }
                }

                // \u2728 Latest Section (Movies, Series & Anime synced regularly)
                val displayLatestList = if (latestReleases.isNotEmpty()) latestReleases else realMediaList.take(15)
                if (displayLatestList.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MediaCategoryRowSection(
                                title = "\u2728 Latest",
                                items = displayLatestList,
                                onSeeAllClick = { onNavigateToMediaTab("Latest") },
                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                onLoadMore = { viewModel.loadMoreMedia() }
                            )
                        }
                    }
                }

                // 3. Secret Pot Settings / Movie Picks Section (CONNECTED TO REAL TMDB DATA)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "HOT ",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        brush = Brush.horizontalGradient(
                                            if (isDark) listOf(Color(0xFFFFFFFF), Color(0xFFB0B0B0), Color(0xFF757575))
                                            else listOf(Color(0xFF000000), Color(0xFF333333), Color(0xFF666666))
                                        )
                                    )
                                )
                                Text(
                                    text = "AIR",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        brush = Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFF3D00)))
                                    )
                                )
                            }

                            Text(
                                text = "View All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B00),
                                modifier = Modifier.clickable { onNavigateToMediaTab("Movies") }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (realMediaList.isEmpty()) {
                            ShimmerPosterRow(itemCount = 4)
                        } else {
                            val listState = rememberLazyListState()
                            LaunchedEffect(realMediaList) {
                                while (true) {
                                    kotlinx.coroutines.delay(4000)
                                    val currentItem = listState.firstVisibleItemIndex
                                    val nextItem = if (currentItem < realMediaList.size - 1) currentItem + 1 else 0
                                    listState.animateScrollToItem(nextItem)
                                }
                            }
                            LazyRow(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(realMediaList, key = { index, mediaItem -> "${mediaItem.id}_$index" }) { index, mediaItem ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF1C1C1E),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(150.dp)
                                            .clickable {
                                                viewModel.preScrapeMediaItem(mediaItem)
                                                selectedItemForDetail = mediaItem
                                            }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Real Poster Image
                                            if (mediaItem.imageUrl.isNotEmpty()) {
                                                ShimmerAsyncImage(
                                                    model = mediaItem.imageUrl,
                                                    contentDescription = mediaItem.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Infinite Video & Episodes Continuous Stream Feed (Directly Below Hot Air Section)
                if (effectiveDiscoverFeedList.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Subscriptions,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DISCOVER FEED",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = if (isDark) Color.White else Color(0xFF1C1C1E),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = "Infinite Stream",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B00)
                            )
                        }
                    }

                    // Feed Cards directly in LazyColumn for optimal rendering & continuous on-demand scrolling
                    itemsIndexed(effectiveDiscoverFeedList, key = { _, item -> "feed_" + item.id }) { index, feedItem ->
                        if (index >= effectiveDiscoverFeedList.size - 3) {
                            LaunchedEffect(effectiveDiscoverFeedList.size) {
                                viewModel.loadMoreMedia()
                            }
                        }

                        com.example.ui.components.VerticalMediaFeedCard(
                            item = feedItem,
                            isPlayingPreview = activePreviewFeedItemId == feedItem.id,
                            onStartPreview = {
                                activePreviewFeedItemId = feedItem.id
                            },
                            onStopPreview = {
                                if (activePreviewFeedItemId == feedItem.id) {
                                    activePreviewFeedItemId = null
                                }
                            },
                            onPlayClick = {
                                activePreviewFeedItemId = null
                                viewModel.playMediaItem(feedItem)
                                onNavigateToPlayer()
                            },
                            onInfoClick = {
                                viewModel.preScrapeMediaItem(feedItem)
                                selectedItemForDetail = feedItem
                            },
                            viewModel = viewModel
                        )
                    }
                }

                // 5. Developer Footer
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    DeveloperNoteFooter(modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            }
        } else {
            // VIEW 2: IPTV NODES & PLAYLIST / CHANNEL BROWSER

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Category Filter Row
        if (selectedPlaylist == null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories, key = { it }) { category ->
                    val isSelected = category == selectedCategoryFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (category == "Fixed Channels") {
                                val fixedPlaylist = filteredPlaylists.find { it.name.contains("Fixed Channels", ignoreCase = true) }
                                if (fixedPlaylist != null) {
                                    viewModel.selectPlaylist(fixedPlaylist)
                                }
                            } else {
                                selectedCategoryFilter = category
                            }
                        },
                        label = {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple,
                            selectedLabelColor = Color.White,
                            containerColor = DeepSlate,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BorderColor,
                            selectedBorderColor = NeonPurple,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Active Player Card at Top when channel is playing
        if (selectedPlaylist != null && activeChannel != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                Column {
                    val fallbackUrl = remember(activeChannel!!.url) { viewModel.getBackupChannel(activeChannel!!.name)?.url }
                    val isBatterySaverMode by viewModel.batterySaverMode.collectAsState()
                    ExoPlayerView(
                        streamUrl = activeChannel!!.url,
                        channelName = activeChannel!!.name,
                        customHeaders = activeChannel!!.headers,
                        isFullScreen = false,
                        isInPipMode = false,
                        fallbackUrl = fallbackUrl,
                        onFullScreenToggle = { onNavigateToPlayer() },
                        onPlaybackError = { err -> viewModel.logPlaybackError(activeChannel!!.name, err) },
                        onPlaybackSuccess = { viewModel.clearPlaybackError() },
                        onAutoNext = { viewModel.playNextChannel() },
                        onAutoPrev = { viewModel.playPrevChannel() },
                        isBatterySaverMode = isBatterySaverMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepSlate)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeChannel!!.name,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "NOW PLAYING",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        IconButton(
                            onClick = { onNavigateToPlayer() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Theater Mode",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Main List Content
        if (selectedPlaylist == null) {
            // Display sub-playlists
            when (val state = playlistsState) {
                is UiState.Loading -> {
                    ShimmerPosterGrid(
                        columns = 2,
                        itemCount = 6,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is UiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.loadPlaylists() }
                    )
                }
                is UiState.Success<*> -> {
                    if (displayPlaylists.isEmpty()) {
                        EmptyStateView(
                            title = "No playlists found",
                            tip = "Try entering a different keyword or category."
                        )
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item(span = { GridItemSpan(2) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val targetPlaylist = remember(filteredPlaylists) {
                                        filteredPlaylists.find { it.name.contains("UPDATE CHANNELS", ignoreCase = true) }
                                    }
                                    if (targetPlaylist != null) {
                                        Card(
                                            onClick = { viewModel.selectPlaylist(targetPlaylist) },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = DeepSlate),
                                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                NeonCyan.copy(alpha = 0.15f),
                                                                NeonPurple.copy(alpha = 0.05f)
                                                            )
                                                        )
                                                    )
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .background(NeonCyan.copy(alpha = 0.2f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tv,
                                                        contentDescription = "Updated Channels",
                                                        tint = NeonCyan,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "UPDATED CHANNELS",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color.Red, RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "LIVE",
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Tap to explore auto-updating premium TV channels",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = "Go",
                                                    tint = NeonCyan.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    val fixedPlaylist = remember(filteredPlaylists) {
                                        filteredPlaylists.find { it.name.contains("Fixed Channels", ignoreCase = true) }
                                    }
                                    if (fixedPlaylist != null) {
                                        Card(
                                            onClick = { viewModel.selectPlaylist(fixedPlaylist) },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = DeepSlate),
                                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                NeonPurple.copy(alpha = 0.15f),
                                                                NeonCyan.copy(alpha = 0.05f)
                                                            )
                                                        )
                                                    )
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .background(NeonPurple.copy(alpha = 0.2f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tv,
                                                        contentDescription = "Fixed Channels",
                                                        tint = NeonPurple,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "Fixed Channels ",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(NeonPurple, RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "FIXED",
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Tap to stream pre-configured fixed channels",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = "Go",
                                                    tint = NeonPurple.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    val sportTvPlaylist = remember(filteredPlaylists) {
                                        filteredPlaylists.find { it.name.contains("Sport TV", ignoreCase = true) }
                                    }
                                    if (sportTvPlaylist != null) {
                                        Card(
                                            onClick = { viewModel.selectPlaylist(sportTvPlaylist) },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = DeepSlate),
                                            border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                Color(0xFFFF6B00).copy(alpha = 0.15f),
                                                                NeonPurple.copy(alpha = 0.05f)
                                                            )
                                                        )
                                                    )
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .background(Color(0xFFFF6B00).copy(alpha = 0.2f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.SportsSoccer,
                                                        contentDescription = "Sport TV",
                                                        tint = Color(0xFFFF6B00),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "Sport TV ",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFFFF6B00), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "T-SPORTS",
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Tap to stream T Sports (1080) live broadcast",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = "Go",
                                                    tint = Color(0xFFFF6B00).copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            itemsIndexed(items = displayPlaylists, key = { index, playlist -> "${playlist.url}_$index" }) { _, playlist ->
                                PlaylistGridCard(
                                    playlist = playlist,
                                    onClick = { viewModel.selectPlaylist(playlist) }
                                )
                            }
                            item(span = { GridItemSpan(2) }) {
                                Spacer(modifier = Modifier.height(8.dp))
                                DeveloperNoteFooter()
                            }
                        }
                    }
                }
            }
        } else {
            // Display parsed channels
            when (val state = channelsState) {
                is UiState.Loading -> {
                    ShimmerPosterGrid(
                        columns = 2,
                        itemCount = 6,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is UiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.selectPlaylist(selectedPlaylist!!) }
                    )
                }
                is UiState.Success<*> -> {
                    val availableGroups by viewModel.availableChannelGroups.collectAsState()
                    val selectedGroup by viewModel.selectedChannelGroup.collectAsState()

                    Column(modifier = Modifier.weight(1f)) {
                        if (availableGroups.size > 1) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(availableGroups, key = { it }) { group ->
                                    val isSelected = group == selectedGroup
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setSelectedChannelGroup(group) },
                                        label = {
                                            Text(
                                                text = group,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan,
                                            selectedLabelColor = Color.Black,
                                            containerColor = DeepSlate,
                                            labelColor = TextPrimary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = BorderColor,
                                            selectedBorderColor = NeonCyan
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (filteredChannels.isEmpty()) {
                            EmptyStateView(
                                title = "No channels found",
                                tip = if (selectedGroup != "All") "Try selecting another category or clearing search." else "Try searching with other terms."
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(items = filteredChannels, key = { index, channel -> "${channel.url}_${channel.name}_$index" }) { _, channel ->
                                    val isFav by viewModel.isFavoriteStream(channel.url).collectAsState(false)
                                    val activeCh by viewModel.activeChannel.collectAsState()
                                    val errUrl by viewModel.playbackErrorChannelUrl.collectAsState()
                                    val isSelected = channel.url == activeCh?.url
                                    val hasError = isSelected && errUrl == channel.url
                                    ChannelListRow(
                                        channel = channel,
                                        isFavorite = isFav,
                                        onFavoriteToggle = { viewModel.toggleFavorite(channel, isFav) },
                                        onClick = {
                                            viewModel.setActiveChannel(channel)
                                        },
                                        onFloatClick = { viewModel.addFloatingPlayer(channel = channel) },
                                        isSelected = isSelected,
                                        hasError = hasError,
                                        isPremium = viewModel.isChannelPremium(channel),
                                        onHideClick = { viewModel.toggleHideChannel(channel, true) },
                                        onMoveUpClick = { viewModel.moveChannel(channel, up = true, activeList = filteredChannels) },
                                        onMoveDownClick = { viewModel.moveChannel(channel, up = false, activeList = filteredChannels) }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DeveloperNoteFooter()
                                }
                            }
                        }
                    }
                }
            }
        }
            }

            // Clickable Scrim Overlay for dismissing search when clicking empty area
            if (isIptvView && isIptvSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .zIndex(2f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isIptvSearchActive = false
                            viewModel.setPlaylistSearchQuery("")
                            viewModel.setChannelSearchQuery("")
                        }
                )
            }
        }
    }
}

        activeWebPlayer?.let { state ->
            val imdbId = state.item.imdbId ?: "tt0111161"
            com.example.ui.components.CinemetaWebViewPlayer(
                imdbId = imdbId,
                title = state.item.title,
                type = state.item.type,
                season = state.season,
                episode = state.episode,
                allMediaItems = realMediaList,
                onSelectMedia = { selectedItem ->
                    viewModel.playMediaItem(selectedItem, 1, 1)
                                                        onNavigateToPlayer()
                },
                onClosePlayer = { activeWebPlayer = null },
                onFullScreenChange = { isFull ->
                    viewModel.setFullScreen(isFull)
                },
                modifier = Modifier.fillMaxSize(),
                isInPipMode = isInPipMode,
                onPlayingStateChanged = { playing ->
                    viewModel.setMediaPlaying(playing)
                },
                viewModel = viewModel,
                mediaItem = state.item
            )
        }
    }

    selectedItemForDetail?.let { item ->
        val isFavorite by viewModel.isMediaFavoriteStream(item.id).collectAsState(initial = false)
        MediaDetailSheet(
            item = item,
            isFavorite = isFavorite,
            onFavoriteToggle = { viewModel.toggleMediaFavorite(item, isFavorite) },
            onDismiss = { selectedItemForDetail = null },
            onPlayStream = { season, episode ->
                selectedItemForDetail = null
                viewModel.playMediaItem(item, season, episode)
                                                        onNavigateToPlayer()
            },
            viewModel = viewModel
        )
    }

    if (showSignInSheet) {
        SignInBottomSheet(
            viewModel = viewModel,
            onDismiss = { showSignInSheet = false }
        )
    }

    if (showProfileSheet) {
        UserProfileBottomSheet(
            profile = userProfile,
            selectedAudioIndex = selectedAudioIndex,
            onDismiss = { showProfileSheet = false },
            onShowWatchHistory = {
                showProfileSheet = false
                showWatchHistorySheet = true
            },
            onShowWebVersion = {
                showProfileSheet = false
                showWebVersionView = true
            },
            onSwitchAccount = {
                showProfileSheet = false
                showSignInSheet = true
            },
            onSignOut = {
                viewModel.logout()
                showProfileSheet = false
            },
            onDeleteAccount = {
                showProfileSheet = false
                showDeleteAccountDialog = true
            },
            onShowCopyrightAlert = {
                showProfileSheet = false
                showCopyrightSheet = true
            },
            onShowFloatingPlayerLimit = {
                showProfileSheet = false
                showFloatingPlayerLimitSheet = true
            },
            onShowMasterAnime = {
                showProfileSheet = false
                showMasterAnimeBrowser = true
            },
            onShowCineStream = {
                showProfileSheet = false
                showCineStreamBrowser = true
            }
        )
    }

    if (showMasterAnimeBrowser) {
        MasterAnimeBrowserModal(
            url = "https://media.hmair.xyz",
            onDismiss = { showMasterAnimeBrowser = false }
        )
    }

    if (showCineStreamBrowser) {
        MasterAnimeBrowserModal(
            url = "https://cine.hmair.xyz/",
            onDismiss = { showCineStreamBrowser = false }
        )
    }

    if (showCopyrightSheet) {
        CopyrightBottomSheet(
            onDismiss = { showCopyrightSheet = false }
        )
    }

    if (showFloatingPlayerLimitSheet) {
        FloatingPlayerLimitBottomSheet(
            currentLimit = maxFloatingPlayers,
            onSelectLimit = { limit ->
                viewModel.setMaxFloatingPlayers(limit)
                Toast.makeText(context, "Floating player limit set to $limit", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showFloatingPlayerLimitSheet = false }
        )
    }

    if (showWatchHistorySheet) {
        WatchHistorySheet(
            viewModel = viewModel,
            onNavigateToPlayer = onNavigateToPlayer,
            onDismiss = { showWatchHistorySheet = false }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Delete Account", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete your account? This action cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount {
                            showDeleteAccountDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showWebVersionView) {
        WebBrowserDialog(
            url = "https://homeairtv.xubilaswebdevcorp.shop/",
            onDismiss = { showWebVersionView = false }
        )
    }

    if (showLiveTvBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLiveTvBottomSheet = false },
            containerColor = homeCardBg,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Live TV Options",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = homeTextColor
                )
                Text(
                    text = "Select option to proceed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = homeSubTextColor,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live option circular button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showLiveTvBottomSheet = false
                            onNavigateToPlayer()
                            viewModel.playHomeAirTvDirect {}
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(
                                    color = Color(0xFFFF6B00).copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 2.dp,
                                    color = Color(0xFFFF6B00),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LiveTv,
                                contentDescription = "Live",
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = homeTextColor
                        )
                    }

                    // Sports option circular button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showLiveTvBottomSheet = false
                            isSportsViewMode = true
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(
                                    color = Color(0xFFFF6B00).copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = Color(0xFFFF6B00),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsSoccer,
                                contentDescription = "Sports",
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sports",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = homeTextColor
                        )
                    }

                    // Dashboard option circular button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showLiveTvBottomSheet = false
                            isLiveTvViewMode = true
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(
                                    color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFEF),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = homeBorderColor,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = "Dashboard",
                                tint = homeIconTint,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = homeTextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(text = placeholder, color = TextSecondary, fontSize = 14.sp) },
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = DeepSlate,
            unfocusedContainerColor = DeepSlate,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .testTag("search_bar")
    )
}

@Composable
fun PlaylistGridCard(
    playlist: IptvPlaylist,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "elevation"
    )

    // Dynamic gradient accents based on playlist name/group
    val gradientColors = remember(playlist.name) {
        val hash = playlist.name.hashCode()
        when (kotlin.math.abs(hash) % 4) {
            0 -> listOf(NeonCyan, NeonPurple)
            1 -> listOf(NeonPurple, NeonMagenta)
            2 -> listOf(Color(0xFFF59E0B), NeonMagenta)
            else -> listOf(NeonCyan, Color(0xFF10B981))
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            }
            .shadow(6.dp, RoundedCornerShape(22.dp), spotColor = gradientColors[0].copy(alpha = 0.3f))
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(gradientColors.map { it.copy(alpha = 0.6f) })
                ),
                RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            gradientColors[0].copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Group Chip Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(gradientColors[0].copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = playlist.group.ifBlank { "GLOBAL" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = gradientColors[0]
                    )
                }

                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Live HD",
                    tint = gradientColors[1],
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = gradientColors.map { it.copy(alpha = 0.2f) }
                        )
                    )
                    .border(1.5.dp, Brush.linearGradient(gradientColors), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.logo.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(playlist.logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Region",
                        tint = gradientColors[0],
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberGray)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Parse Streams",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = TextSecondary
                )
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Open",
                    tint = NeonCyan,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun ChannelListRow(
    channel: IptvChannel,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    onFloatClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    hasError: Boolean = false,
    isPremium: Boolean = false,
    onHideClick: (() -> Unit)? = null,
    onMoveUpClick: (() -> Unit)? = null,
    onMoveDownClick: (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 4.dp,
        label = "elevation"
    )

    val context = LocalContext.current


    val imageRequest = remember(channel.logo) {
        ImageRequest.Builder(context)
            .data(channel.logo)
            .crossfade(true)
            .diskCacheKey(channel.logo)
            .memoryCacheKey(channel.logo)
            .build()
    }

    val highlightColor = when {
        isSelected && hasError -> Color(0xFFFF0000)
        isSelected -> Color(0xFF00E676)
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlightColor != null) highlightColor.copy(alpha = 0.12f) else DeepSlate
        ),
        border = if (highlightColor != null) BorderStroke(2.dp, highlightColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Icon/Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightAccent.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play Icon",
                        tint = NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        color = highlightColor ?: TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPremium) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "VIP",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Text(
                    text = channel.group.ifBlank { "Live TV" },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                var showMenu by remember { mutableStateOf(false) }

                if (onFloatClick != null) {
                    IconButton(onClick = onFloatClick) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Float PIP Player",
                            tint = NeonCyan
                        )
                    }
                }
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) NeonMagenta else TextSecondary
                    )
                }

                if (onHideClick != null || onMoveUpClick != null || onMoveDownClick != null) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Customize Channel",
                                tint = TextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DeepSlate)
                        ) {
                            if (onMoveUpClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Move Up", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = NeonCyan) },
                                    onClick = {
                                        showMenu = false
                                        onMoveUpClick()
                                    }
                                )
                            }
                            if (onMoveDownClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Move Down", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = NeonCyan) },
                                    onClick = {
                                        showMenu = false
                                        onMoveDownClick()
                                    }
                                )
                            }
                            if (onHideClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Hide / Disable", color = Color.Red) },
                                    leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        onHideClick()
                                    }
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Play",
                    tint = NeonCyan,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}


// ==========================================
// 3. THEATRE PLAYER SCREEN
// ==========================================
@Composable
fun PlayerScreen(isMiniPlayer: Boolean = false, onMiniPlayerToggle: () -> Unit = {}, 
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier,
    isInPipMode: Boolean = false,
    onBackPress: () -> Unit = {}
) {
    val activeChannel by viewModel.activeChannel.collectAsState()
    val activeMediaItem by viewModel.activeMediaItem.collectAsState()
    val activeMediaStreamUrl by viewModel.activeMediaStreamUrl.collectAsState()
    val activeMediaSeason by viewModel.activeMediaSeason.collectAsState()
    val activeMediaEpisode by viewModel.activeMediaEpisode.collectAsState()
    val isFullScreen by viewModel.isFullScreen.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val mediaWatchHistory by viewModel.mediaWatchHistory.collectAsState()
    val mediaState by viewModel.mediaState.collectAsState()
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    val context = LocalContext.current


    val activity = context as? android.app.Activity

    var isSelectMode by remember { mutableStateOf(false) }
    val selectedChannelsForCategory = remember { mutableStateListOf<IptvChannel>() }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var reorderingChannelUrl by remember { mutableStateOf<String?>(null) }
    val selectedCategory by viewModel.selectedChannelGroup.collectAsState()
    val verticalChannels = filteredChannels



    DisposableEffect(activeChannel, activeMediaItem) {
        val window = activity?.window
        if (activeChannel != null || activeMediaItem != null) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setFullScreen(false)
        }
    }

    DisposableEffect(isFullScreen) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        if (isFullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window?.attributes = window?.attributes?.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window?.attributes = window?.attributes?.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (isFullScreen) {
            viewModel.setFullScreen(false)
        } else {
            onBackPress()
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val playerBgColor = if (isDark) SpaceBlack else Color(0xFFF5F5F7)
    val playerCardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val playerTextColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val playerSubTextColor = if (isDark) Color(0xFFA1A1A6) else Color(0xFF636366)
    val playerIconTint = if (isDark) Color.White else Color(0xFF1C1C1E)
    val playerChipBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val playerBorderColor = if (isDark) Color.Gray.copy(alpha = 0.3f) else Color(0xFFE5E5EA)

    LaunchedEffect(activeChannel, activeMediaItem) {
        if (activeChannel == null && activeMediaItem == null) {
            kotlinx.coroutines.delay(3500)
            if (activeChannel == null && activeMediaItem == null) {
                val history = viewModel.mediaWatchHistory.value
                val firstHistory = history.firstOrNull()
                if (firstHistory != null) {
                    viewModel.playMediaItem(firstHistory, 1, 1)
                } else {
                    onBackPress()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(playerBgColor)
    ) {
        if (activeChannel == null && activeMediaItem == null) {
            // Player loading state while auto-launching stream with quick back navigation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = onBackPress,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = NeonCyan
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(18.dp))
                val airOrangeGradient = Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFF3D00)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Launching ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "AIR",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            brush = airOrangeGradient
                        )
                    )
                    Text(
                        text = " Player...",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "Thanks for choosing Home ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "AIR",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            brush = airOrangeGradient
                        )
                    )
                }
            }
        }
    } else if (activeMediaItem != null) {
            val item = activeMediaItem!!
            val allMedia = ((mediaState as? UiState.Success<*>)?.data as? List<*>)?.filterIsInstance<MediaItem>() ?: emptyList()
            com.example.ui.components.CinemetaWebViewPlayer(
                imdbId = item.imdbId ?: item.id,
                title = item.title,
                type = item.type,
                nativeStreamUrl = activeMediaStreamUrl,
                season = activeMediaSeason,
                episode = activeMediaEpisode,
                allMediaItems = allMedia,
                onSelectMedia = { selectedItem ->
                    viewModel.playMediaItem(selectedItem)
                },
                onClosePlayer = {
                    viewModel.clearActivePlayer()
                    onBackPress()
                },
                onFullScreenChange = { isFull ->
                    viewModel.setFullScreen(isFull)
                },
                modifier = Modifier.fillMaxSize(),
                isInPipMode = isInPipMode || isMiniPlayer,
                isFullScreen = isFullScreen,
                onMiniPlayerToggle = onMiniPlayerToggle,
                onPlayingStateChanged = { playing ->
                    viewModel.setMediaPlaying(playing)
                },
                viewModel = viewModel,
                mediaItem = item
            )
        } else {
            // Real Player
            val channel = activeChannel!!
            val fallbackUrl = remember(channel.url) { viewModel.getBackupChannel(channel.name)?.url }
            val isFav by viewModel.isFavoriteStream(channel.url).collectAsState(false)
            val filteredChannels by viewModel.filteredChannels.collectAsState()
            val channelSearchQuery by viewModel.channelSearchQuery.collectAsState()
            val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
            val isPlayerPlaying by viewModel.isPlayerPlaying.collectAsState()
            val playbackErrorChannelUrl by viewModel.playbackErrorChannelUrl.collectAsState()
            val playerListState = rememberLazyListState()

            var isSearchVisible by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isFullScreen && !isInPipMode && !isMiniPlayer) Modifier.statusBarsPadding() else Modifier)
            ) {
                // 1. Embed Media3 player view directly at top of screen
                val isBatterySaverMode by viewModel.batterySaverMode.collectAsState()
                ExoPlayerView(
                    streamUrl = channel.url,
                    channelName = channel.name,
                    customHeaders = channel.headers,
                    isFullScreen = isFullScreen,
                    isInPipMode = isInPipMode || isMiniPlayer,
                    onMiniPlayerToggle = onMiniPlayerToggle,
                    fallbackUrl = fallbackUrl,
                    onFullScreenToggle = { viewModel.setFullScreen(!isFullScreen) },
                    onPlaybackError = { err ->
                        viewModel.logPlaybackError(channel.name, err)
                    },
                    onPlaybackSuccess = { viewModel.clearPlaybackError() },
                    onAutoNext = { viewModel.playNextChannel() },
                    onAutoPrev = { viewModel.playPrevChannel() },
                    externalIsPlaying = isPlayerPlaying,
                    onPlayPauseToggle = { viewModel.setPlayerPlaying(it) },
                    onBack = onBackPress,
                    channels = filteredChannels,
                    onSelectChannel = { selectedCh -> viewModel.setActiveChannel(selectedCh) },
                    isBatterySaverMode = isBatterySaverMode,
                    modifier = if (isFullScreen || isInPipMode) {
                        Modifier.fillMaxSize().background(Color.Black)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    }
                )

                if (!isFullScreen && !isInPipMode && !isMiniPlayer) {
                    // Portrait contents under the player
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Title Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = playerTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFF6B00).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFFF6B00).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = channel.group.ifBlank { "Live TV" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = Color(0xFFFF6B00)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Toggle favorite button
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(channel, isFav) },
                                    modifier = Modifier
                                        .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f), CircleShape)
                                        .size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Add favorite",
                                        tint = if (isFav) Color(0xFFFF2D55) else playerIconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Layout toggle grid button next to favorite button
                                val useCircularChannelsLayout by viewModel.useCircularChannelsLayout.collectAsState()
                                IconButton(
                                    onClick = { viewModel.toggleChannelsLayout() },
                                    modifier = Modifier
                                        .background(
                                            if (useCircularChannelsLayout) Color(0xFF00E676).copy(alpha = 0.2f)
                                            else if (isDark) Color.White.copy(alpha = 0.1f)
                                            else Color.Black.copy(alpha = 0.06f),
                                            CircleShape
                                        )
                                        .size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Toggle Channel Layout",
                                        tint = if (useCircularChannelsLayout) Color(0xFF00E676) else playerIconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isSelectMode = !isSelectMode
                                        if (!isSelectMode) {
                                            selectedChannelsForCategory.clear()
                                            showCreateCategoryDialog = false
                                        }
                                    },
                                    modifier = Modifier
                                        .background(
                                            if (isSelectMode) NeonCyan.copy(alpha = 0.25f)
                                            else if (isDark) Color.White.copy(alpha = 0.1f)
                                            else Color.Black.copy(alpha = 0.06f),
                                            CircleShape
                                        )
                                        .size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAddCheck,
                                        contentDescription = "Select Channels for Custom Category",
                                        tint = if (isSelectMode) NeonCyan else playerIconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Switcher Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick Channel Switcher",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = playerTextColor
                            )
                            Text(
                                text = "${filteredChannels.size} Channels",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                color = Color(0xFFFF6B00)
                            )
                        }

                        // Horizontal Suggested Channels Row (Quick Switcher)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(filteredChannels, key = { index, item -> "${item.url}_${item.name}_$index" }) { _, item ->
                                val isSelected = item.url == channel.url
                                val hasError = isSelected && playbackErrorChannelUrl == item.url
                                val highlightColor = when {
                                    hasError -> Color(0xFFFF0000)
                                    isSelected -> Color(0xFFFF6B00)
                                    else -> null
                                }
                                val context = LocalContext.current


                                val imageRequest = remember(item.logo) {
                                    ImageRequest.Builder(context)
                                        .data(item.logo)
                                        .crossfade(true)
                                        .build()
                                }

                                Card(
                                    modifier = Modifier
                                        .width(170.dp)
                                        .height(52.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { viewModel.setActiveChannel(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (hasError) Color(0xFFFF0000).copy(alpha = 0.15f) else if (isSelected) Color(0xFFFF6B00).copy(alpha = 0.15f) else playerCardBg
                                    ),
                                    border = BorderStroke(
                                        width = if (highlightColor != null) 1.5.dp else 1.dp,
                                        color = highlightColor ?: playerBorderColor
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.logo.isNotBlank()) {
                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Tv,
                                                    contentDescription = null,
                                                    tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.name,
                                            color = highlightColor ?: playerTextColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category Filters Row & Fluid Search Bar
                        val categories by viewModel.availableChannelGroups.collectAsState(listOf("All"))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Ultra Smooth Fluid Animated Search Box
                            AnimatedVisibility(
                                visible = isSearchVisible,
                                enter = expandVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(animationSpec = tween(300)),
                                exit = shrinkVertically(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeOut(animationSpec = tween(200))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SearchBar(
                                                query = channelSearchQuery,
                                                onQueryChange = { viewModel.setChannelSearchQuery(it) },
                                                placeholder = "Search channels...",
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    isSearchVisible = false
                                                    viewModel.setChannelSearchQuery("")
                                                },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(playerChipBg, CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close Search",
                                                    tint = playerIconTint
                                                )
                                            }
                                        }

                                        if (channelSearchQuery.length >= 2) {
                                            val suggestions = filteredChannels.take(5)
                                            if (suggestions.isNotEmpty()) {
                                                Card(
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = CardDefaults.cardColors(containerColor = playerCardBg),
                                                    border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.6f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 6.dp)
                                                        .zIndex(10f)
                                                ) {
                                                    Column {
                                                        suggestions.forEach { sugChannel ->
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        viewModel.setChannelSearchQuery("")
                                                                        isSearchVisible = false
                                                                        viewModel.setActiveChannel(sugChannel)
                                                                    }
                                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Tv,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFFFF6B00),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Text(
                                                                    text = sugChannel.name,
                                                                    color = playerTextColor,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.SemiBold
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

                            // Category List with Search Button Chip at start (left side)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Search Button Chip at start of category row
                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isSearchVisible || channelSearchQuery.isNotEmpty())
                                                    Color(0xFFFF6B00)
                                                else
                                                    playerChipBg
                                            )
                                            .clickable {
                                                isSearchVisible = !isSearchVisible
                                                if (!isSearchVisible) {
                                                    viewModel.setChannelSearchQuery("")
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Search",
                                                tint = if (isSearchVisible || channelSearchQuery.isNotEmpty()) Color.White else playerTextColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            AnimatedVisibility(
                                                visible = isSearchVisible || channelSearchQuery.isNotEmpty(),
                                                enter = expandHorizontally(
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                ) + fadeIn(),
                                                exit = shrinkHorizontally(
                                                    animationSpec = spring(
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                ) + fadeOut()
                                            ) {
                                                Text(
                                                    text = "Search",
                                                    color = if (isSearchVisible || channelSearchQuery.isNotEmpty()) Color.White else playerTextColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                items(categories, key = { it }) { cat ->
                                    val isSelected = cat == selectedCategory && channelSearchQuery.isEmpty()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) Color(0xFFFF6B00) else playerChipBg)
                                            .clickable {
                                                viewModel.setSelectedChannelGroup(cat)
                                                if (channelSearchQuery.isNotEmpty()) {
                                                    viewModel.setChannelSearchQuery("")
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else playerTextColor,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Channels list (Circular 4-column Grid or Vertical List)
                        val isCircularLayout by viewModel.useCircularChannelsLayout.collectAsState()

                        AnimatedContent(
                            targetState = isCircularLayout,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.96f))
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            label = "ChannelLayoutSwitch"
                        ) { circular ->
                            if (circular) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(verticalChannels, key = { index, vertChannel -> "${index}_${vertChannel.url}_${vertChannel.name}" }) { _, vertChannel ->
                                        val isSelected = vertChannel.url == channel.url
                                        val hasError = isSelected && playbackErrorChannelUrl == vertChannel.url
                                        val circularHighlightColor = when {
                                            hasError -> Color(0xFFFF0000)
                                            isSelected -> Color(0xFF00E676)
                                            else -> null
                                        }
                                        val context = LocalContext.current


                                        val imageRequest = remember(vertChannel.logo) {
                                            ImageRequest.Builder(context)
                                                .data(vertChannel.logo)
                                                .crossfade(true)
                                                .build()
                                        }

                                        val isReorderingThis = reorderingChannelUrl == vertChannel.url
                                        val isChannelSelectedInSelectMode = isSelectMode && selectedChannelsForCategory.any { it.url == vertChannel.url }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    scaleX = if (isReorderingThis) 1.15f else 1f
                                                    scaleY = if (isReorderingThis) 1.15f else 1f
                                                    alpha = if (isReorderingThis) 0.85f else 1f
                                                }
                                                .pointerInput(vertChannel.url) {
                                                    var dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            if (!isSelectMode) {
                                                                reorderingChannelUrl = vertChannel.url
                                                            }
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            if (!isSelectMode) {
                                                                reorderingChannelUrl = vertChannel.url
                                                                dragOffset += dragAmount
                                                                 if (dragOffset.x > 25f) {
                                                                    viewModel.moveChannelByDelta(vertChannel, 1, verticalChannels)
                                                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                                } else if (dragOffset.x < -25f) {
                                                                    viewModel.moveChannelByDelta(vertChannel, -1, verticalChannels)
                                                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                                } else if (dragOffset.y > 25f) {
                                                                    viewModel.moveChannelByDelta(vertChannel, 4, verticalChannels)
                                                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                                } else if (dragOffset.y < -25f) {
                                                                    viewModel.moveChannelByDelta(vertChannel, -4, verticalChannels)
                                                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = { reorderingChannelUrl = null },
                                                        onDragCancel = { reorderingChannelUrl = null }
                                                    )
                                                }
                                                .combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {
                                                        if (isSelectMode) {
                                                            if (selectedChannelsForCategory.any { it.url == vertChannel.url }) {
                                                                selectedChannelsForCategory.removeAll { it.url == vertChannel.url }
                                                            } else {
                                                                selectedChannelsForCategory.add(vertChannel)
                                                            }
                                                        } else {
                                                            viewModel.setActiveChannel(vertChannel)
                                                        }
                                                    }
                                                )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(
                                                        color = if (circularHighlightColor != null) circularHighlightColor.copy(alpha = 0.25f) else Color.White,
                                                        shape = CircleShape
                                                    )
                                                    .border(
                                                        width = if (isChannelSelectedInSelectMode) 3.5.dp else if (isSelected) 3.dp else 1.dp,
                                                        color = if (isChannelSelectedInSelectMode) NeonCyan else circularHighlightColor ?: (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                                        shape = CircleShape
                                                    )
                                                    .padding(if (isSelected) 4.dp else 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (vertChannel.logo.isNotBlank()) {
                                                    AsyncImage(
                                                        model = imageRequest,
                                                        contentDescription = vertChannel.name,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Tv,
                                                        contentDescription = vertChannel.name,
                                                        tint = Color(0xFFFF6B00),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                // VIP overlay for premium channels
                                                if (viewModel.isChannelPremium(vertChannel)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(1.dp)
                                                            .align(Alignment.TopEnd)
                                                            .background(
                                                                Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
                                                                CircleShape
                                                            )
                                                            .size(14.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = "VIP",
                                                            tint = Color.Black,
                                                            modifier = Modifier.size(9.dp)
                                                        )
                                                    }
                                                }

                                                if (isReorderingThis) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                                                    ) {
                                                        // Up button
                                                        IconButton(
                                                            onClick = { viewModel.moveChannelByDelta(vertChannel, -4, verticalChannels) },
                                                            modifier = Modifier.align(Alignment.TopCenter).size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                                        }
                                                        // Down button
                                                        IconButton(
                                                            onClick = { viewModel.moveChannelByDelta(vertChannel, 4, verticalChannels) },
                                                            modifier = Modifier.align(Alignment.BottomCenter).size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                                        }
                                                        // Left button
                                                        IconButton(
                                                            onClick = { viewModel.moveChannelByDelta(vertChannel, -1, verticalChannels) },
                                                            modifier = Modifier.align(Alignment.CenterStart).size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Move Left", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                                        }
                                                        // Right button
                                                        IconButton(
                                                            onClick = { viewModel.moveChannelByDelta(vertChannel, 1, verticalChannels) },
                                                            modifier = Modifier.align(Alignment.CenterEnd).size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Move Right", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                                        }
                                                        // Center check (Done)
                                                        IconButton(
                                                            onClick = { reorderingChannelUrl = null },
                                                            modifier = Modifier.align(Alignment.Center).size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.Check, contentDescription = "Done", tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = vertChannel.name,
                                                color = circularHighlightColor ?: playerTextColor,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Text(
                                                text = vertChannel.group.ifBlank { "Bangladeshi" },
                                                color = playerSubTextColor,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = playerListState,
                                    contentPadding = PaddingValues(bottom = 96.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(verticalChannels, key = { index, vertChannel -> "${index}_${vertChannel.url}_${vertChannel.name}" }) { _, vertChannel ->
                                        val isSelected = vertChannel.url == channel.url
                                        val isFavStream by viewModel.isFavoriteStream(vertChannel.url).collectAsState(false)
                                        val context = LocalContext.current


                                        val imageRequest = remember(vertChannel.logo) {
                                            ImageRequest.Builder(context)
                                                .data(vertChannel.logo)
                                                .crossfade(true)
                                                .build()
                                        }

                                        val isReorderingThis = reorderingChannelUrl == vertChannel.url
                                        val isChannelSelectedInSelectMode = isSelectMode && selectedChannelsForCategory.any { it.url == vertChannel.url }

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                                .graphicsLayer {
                                                    scaleX = if (isReorderingThis) 1.04f else 1f
                                                    scaleY = if (isReorderingThis) 1.04f else 1f
                                                    alpha = if (isReorderingThis) 0.85f else 1f
                                                }
                                                .pointerInput(vertChannel.url) {
                                                    var dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            if (!isSelectMode) {
                                                                reorderingChannelUrl = vertChannel.url
                                                            }
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            if (!isSelectMode) {
                                                                reorderingChannelUrl = vertChannel.url
                                                                dragOffset += dragAmount
                                                                if (dragOffset.y > 70f) {
                                                                    viewModel.moveChannelByDelta(vertChannel, 1, verticalChannels)
                                                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                                } else if (dragOffset.y < -70f) {
                                                                    viewModel.moveChannelByDelta(vertChannel, -1, verticalChannels)
                                                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = { reorderingChannelUrl = null },
                                                        onDragCancel = { reorderingChannelUrl = null }
                                                    )
                                                }
                                                .combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {
                                                        if (isSelectMode) {
                                                            if (selectedChannelsForCategory.any { it.url == vertChannel.url }) {
                                                                selectedChannelsForCategory.removeAll { it.url == vertChannel.url }
                                                            } else {
                                                                selectedChannelsForCategory.add(vertChannel)
                                                            }
                                                        } else {
                                                            viewModel.setActiveChannel(vertChannel)
                                                        }
                                                    }
                                                ),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isChannelSelectedInSelectMode) NeonCyan.copy(alpha = 0.15f) else if (isSelected && playbackErrorChannelUrl == vertChannel.url) Color(0xFFFF0000).copy(alpha = 0.15f) else if (isSelected) Color(0xFFFF6B00).copy(alpha = 0.15f) else playerCardBg
                                            ),
                                            border = BorderStroke(
                                                width = if (isChannelSelectedInSelectMode) 2.5.dp else if (isSelected) 2.dp else 1.dp,
                                                color = if (isChannelSelectedInSelectMode) NeonCyan else if (isSelected && playbackErrorChannelUrl == vertChannel.url) Color(0xFFFF0000) else if (isSelected) Color(0xFFFF6B00) else playerBorderColor
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (vertChannel.logo.isNotBlank()) {
                                                        AsyncImage(
                                                            model = imageRequest,
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.Tv, contentDescription = null, tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = vertChannel.name,
                                                            color = if (isSelected && playbackErrorChannelUrl == vertChannel.url) Color(0xFFFF0000) else playerTextColor,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (viewModel.isChannelPremium(vertChannel)) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(
                                                                        Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = "VIP",
                                                                    color = Color.Black,
                                                                    fontSize = 7.sp,
                                                                    fontWeight = FontWeight.ExtraBold
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = vertChannel.group.ifBlank { "Live TV" },
                                                        color = playerSubTextColor,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.toggleFavorite(vertChannel, isFavStream) }
                                                ) {
                                                    Icon(
                                                        imageVector = if (isFavStream) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                        contentDescription = null,
                                                        tint = if (isFavStream) Color(0xFFFF2D55) else playerSubTextColor
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

        if (isSelectMode && !showCreateCategoryDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                    border = BorderStroke(1.5.dp, NeonCyan),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .imePadding(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = NeonCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "📁 Select Channels",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "${selectedChannelsForCategory.size} selected",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (selectedChannelsForCategory.size == verticalChannels.size) {
                                        selectedChannelsForCategory.clear()
                                    } else {
                                        selectedChannelsForCategory.clear()
                                        selectedChannelsForCategory.addAll(verticalChannels)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    if (selectedChannelsForCategory.size == verticalChannels.size) "Deselect All" else "Select All",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (selectedChannelsForCategory.isNotEmpty()) {
                                        showCreateCategoryDialog = true
                                    }
                                },
                                enabled = selectedChannelsForCategory.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    "Next ->",
                                    color = if (selectedChannelsForCategory.isNotEmpty()) Color.Black else Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    isSelectMode = false
                                    selectedChannelsForCategory.clear()
                                    newCategoryName = ""
                                    showCreateCategoryDialog = false
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        if (showCreateCategoryDialog) {
            ModalBottomSheet(
                onDismissRequest = { showCreateCategoryDialog = false },
                containerColor = Color(0xFF18181C),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📁 Create Group Category",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${selectedChannelsForCategory.size} channels selected for this group",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { showCreateCategoryDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    androidx.compose.foundation.text.BasicTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NeonCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFF24242A), RoundedCornerShape(14.dp))
                            .border(1.5.dp, if (newCategoryName.isNotBlank()) NeonCyan else Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (newCategoryName.isEmpty()) {
                                    Text("Enter category group name...", color = Color.Gray, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showCreateCategoryDialog = false }
                        ) {
                            Text("Cancel", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newCategoryName.isNotBlank() && selectedChannelsForCategory.isNotEmpty()) {
                                    val newName = newCategoryName.trim()
                                    viewModel.createCustomCategory(selectedChannelsForCategory.toList(), newName)
                                    viewModel.setSelectedChannelGroup(newName)
                                    showCreateCategoryDialog = false
                                    isSelectMode = false
                                    selectedChannelsForCategory.clear()
                                    newCategoryName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                            ),
                            enabled = newCategoryName.isNotBlank() && selectedChannelsForCategory.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Create Category",
                                color = if (newCategoryName.isNotBlank() && selectedChannelsForCategory.isNotEmpty()) Color.Black else Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }


    }
}

// ==========================================
// 4. FAVORITES & WATCHED SCREEN
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    isHeaderVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val selectedAudioIndex by viewModel.audioIndex.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Favorites, 1 = History

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) SpaceBlack else Color(0xFFF5F5F7)
    val textColor = if (isDark) TextPrimary else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) TextSecondary else Color(0xFF757575)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(top = 16.dp)
    ) {
        // Tab Header
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + expandVertically(expandFrom = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedTab == 0) com.example.ui.theme.AppTranslation.getString("my_saved_channels", selectedAudioIndex) else com.example.ui.theme.AppTranslation.getString("streaming_history", selectedAudioIndex),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = textColor
                    )
                    Text(
                        text = if (selectedTab == 0) "Quick access to favored nodes" else "Recently viewed HLS IPTV streams",
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor
                    )
                }

                if (selectedTab == 1 && watchHistory.isNotEmpty()) {
                    // Clear History Button
                    TextButton(
                        onClick = { viewModel.clearWatchHistory() },
                        colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(com.example.ui.theme.AppTranslation.getString("clear_all", selectedAudioIndex))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom styled Tab Selection Capsule
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DeepSlate)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 0) CyberGray else Color.Transparent)
                    .clickable { selectedTab = 0 }
                    .testTag("favorites_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Favorites (${favorites.size})",
                    color = if (selectedTab == 0) NeonCyan else TextPrimary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 1) CyberGray else Color.Transparent)
                    .clickable { selectedTab = 1 }
                    .testTag("history_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "History (${watchHistory.size})",
                    color = if (selectedTab == 1) NeonCyan else TextPrimary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List Content
        if (selectedTab == 0) {
            // Favorites
            if (favorites.isEmpty()) {
                EmptyStateView(
                    title = "Your Favorite Box is empty",
                    tip = "Browse regional nodes, load channels, and tap the heart icon on any channel to save them here."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(favorites, key = { index, channel -> "${index}_${channel.url}_${channel.name}" }) { _, channel ->
                        val activeCh by viewModel.activeChannel.collectAsState()
                        val errUrl by viewModel.playbackErrorChannelUrl.collectAsState()
                        val isSelected = channel.url == activeCh?.url
                        val hasError = isSelected && errUrl == channel.url
                        ChannelListRow(
                            channel = channel,
                            isFavorite = true,
                            onFavoriteToggle = { viewModel.toggleFavorite(channel, true) },
                            onClick = {
                                viewModel.setActiveChannel(channel)
                                onNavigateToPlayer()
                            },
                            onFloatClick = { viewModel.addFloatingPlayer(channel = channel) },
                            isSelected = isSelected,
                            hasError = hasError,
                            isPremium = viewModel.isChannelPremium(channel)
                        )
                    }
                }
            }
        } else {
            // History
            if (watchHistory.isEmpty()) {
                EmptyStateView(
                    title = "No watched channels yet",
                    tip = "Any streams you tune into will automatically list here for instant recall."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(watchHistory, key = { index, channel -> "${index}_${channel.url}_${channel.name}" }) { _, channel ->
                        val isFav by viewModel.isFavoriteStream(channel.url).collectAsState(false)
                        val activeCh by viewModel.activeChannel.collectAsState()
                        val errUrl by viewModel.playbackErrorChannelUrl.collectAsState()
                        val isSelected = channel.url == activeCh?.url
                        val hasError = isSelected && errUrl == channel.url
                        ChannelListRow(
                            channel = channel,
                            isFavorite = isFav,
                            onFavoriteToggle = { viewModel.toggleFavorite(channel, isFav) },
                            onClick = {
                                viewModel.setActiveChannel(channel)
                                onNavigateToPlayer()
                            },
                            onFloatClick = { viewModel.addFloatingPlayer(channel = channel) },
                            isSelected = isSelected,
                            hasError = hasError,
                            isPremium = viewModel.isChannelPremium(channel)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistorySheet(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    onDismiss: () -> Unit
) {
    val watchHistory by viewModel.watchHistory.collectAsState()
    val mediaWatchHistory by viewModel.mediaWatchHistory.collectAsState()
    val selectedAudioIndex by viewModel.audioIndex.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.example.ui.theme.AppTranslation.getString("watch_history", selectedAudioIndex),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1C1C1E)
                    )
                }
                val hasItems = if (selectedTab == 0) watchHistory.isNotEmpty() else mediaWatchHistory.isNotEmpty()
                if (hasItems) {
                    TextButton(
                        onClick = {
                            if (selectedTab == 0) viewModel.clearWatchHistory() else viewModel.clearMediaWatchHistory()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(com.example.ui.theme.AppTranslation.getString("clear_all", selectedAudioIndex), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Sub-tabs: Live TV vs Movies/Series
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) DeepSlate else Color(0xFFE5E5EA))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == 0) (if (isDark) CyberGray else Color.White) else Color.Transparent)
                        .clickable { selectedTab = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Live TV (${watchHistory.size})",
                        color = if (selectedTab == 0) NeonCyan else (if (isDark) Color.Gray else Color.DarkGray),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == 1) (if (isDark) CyberGray else Color.White) else Color.Transparent)
                        .clickable { selectedTab = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Movies & Shows (${mediaWatchHistory.size})",
                        color = if (selectedTab == 1) NeonCyan else (if (isDark) Color.Gray else Color.DarkGray),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Tab Contents
            if (selectedTab == 0) {
                if (watchHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No watched TV channels yet",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        // Highlighted "Last Watched Channel" banner at the very top
                        val lastChannel = watchHistory.firstOrNull()
                        if (lastChannel != null) {
                            Text(
                                text = " Last Watched Live Channel",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = NeonCyan.copy(alpha = 0.08f)),
                                border = BorderStroke(1.2.dp, NeonCyan.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clickable {
                                        viewModel.setActiveChannel(lastChannel)
                                        onDismiss()
                                        onNavigateToPlayer()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = lastChannel.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isDark) Color.White else Color(0xFF1C1C1E)
                                            )
                                            Text(
                                                text = "Tap to resume instantly",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.setActiveChannel(lastChannel)
                                            onDismiss()
                                            onNavigateToPlayer()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(NeonCyan, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Resume",
                                            tint = Color.Black,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Remaining Channel history list
                        Text(
                            text = "History List",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(watchHistory, key = { index, channel -> "${index}_${channel.url}_${channel.name}" }) { _, channel ->
                                val isFav by viewModel.isFavoriteStream(channel.url).collectAsState(false)
                                val activeCh by viewModel.activeChannel.collectAsState()
                                val errUrl by viewModel.playbackErrorChannelUrl.collectAsState()
                                ChannelListRow(
                                    channel = channel,
                                    isFavorite = isFav,
                                    onFavoriteToggle = { viewModel.toggleFavorite(channel, isFav) },
                                    onClick = {
                                        viewModel.setActiveChannel(channel)
                                        onDismiss()
                                        onNavigateToPlayer()
                                    },
                                    onFloatClick = { viewModel.addFloatingPlayer(channel = channel) },
                                    isSelected = channel.url == activeCh?.url,
                                    hasError = channel.url == activeCh?.url && errUrl == channel.url,
                                    isPremium = viewModel.isChannelPremium(channel)
                                )
                            }
                        }
                    }
                }
            } else {
                if (mediaWatchHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No watched movies or series yet",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(mediaWatchHistory, key = { it.imdbId + it.title }) { item ->
                            val progressInfo = remember(item.id, item.imdbId) {
                                viewModel.getMediaPlaybackProgress(item.imdbId ?: item.id)
                            }
                            val posMs = progressInfo.first
                            val durMs = progressInfo.second
                            val progressRatio = if (durMs > 0) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0.45f
                            val progressPercent = (progressRatio * 100).toInt()

                            val formatTime: (Long) -> String = { ms ->
                                val totalSec = (ms / 1000).coerceAtLeast(0)
                                val s = totalSec % 60
                                val m = (totalSec / 60) % 60
                                val h = totalSec / 3600
                                if (h > 0) String.format("%dh %02dm %02ds", h, m, s) else String.format("%02dm %02ds", m, s)
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDark) SpaceBlack else Color.White),
                                border = BorderStroke(1.dp, if (isDark) BorderColor else Color(0xFFE5E5EA)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // clicking on card resumes from exact point!
                                        viewModel.playMediaItem(item, startPositionMs = posMs)
                                        onDismiss()
                                        onNavigateToPlayer()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail poster
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DeepSlate)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Title, metadata, and watch progress details
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isDark) TextPrimary else Color(0xFF1C1C1E),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.category}  ${item.year.ifEmpty { "HD" }}",
                                            fontSize = 11.sp,
                                            color = if (isDark) TextSecondary else Color(0xFF8E8E93)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))

                                        if (durMs > 0) {
                                            val remainingMs = (durMs - posMs).coerceAtLeast(0)

                                            // Progress Indicator (exactly like downloads tab!)
                                            LinearProgressIndicator(
                                                progress = { progressRatio },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = if (progressPercent >= 95) Color(0xFF2E7D32) else NeonCyan,
                                                trackColor = (if (isDark) Color.White.copy(alpha = 0.1f) else Color.LightGray)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Watch stats
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (progressPercent >= 95) "Watched" else "Watched: ${formatTime(posMs)}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (progressPercent >= 95) Color(0xFF4CAF50) else NeonCyan
                                                )
                                                Text(
                                                    text = if (progressPercent >= 95) "Completed" else "Left: ${formatTime(remainingMs)}",
                                                    fontSize = 10.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        } else {
                                            // In progress / Tap to resume message
                                            Text(
                                                text = "Tap to resume playback",
                                                fontSize = 10.sp,
                                                color = NeonCyan,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Actions: Start Again & Resume Play Arrow
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Start Again (Replay from 0)
                                        IconButton(
                                            onClick = {
                                                viewModel.saveMediaPlaybackProgress(item.imdbId ?: item.id, 0L, durMs)
                                                viewModel.playMediaItem(item, 1, 1, startPositionMs = 0L)
                                                onDismiss()
                                                onNavigateToPlayer()
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background((if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Replay,
                                                contentDescription = "Start Again",
                                                tint = if (isDark) TextSecondary else Color.DarkGray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // Resume from exact point
                                        IconButton(
                                            onClick = {
                                                viewModel.playMediaItem(item, 1, 1, startPositionMs = posMs)
                                                onDismiss()
                                                onNavigateToPlayer()
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(NeonCyan, CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Resume",
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
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

@Composable
fun AdminScreen(
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val logs by viewModel.adminLogs.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val playlistsState by viewModel.playlistsState.collectAsState()
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    val premiumMedia by viewModel.premiumMedia.collectAsState()
    val mediaState by viewModel.mediaState.collectAsState()
    val mediaSearchQuery by viewModel.mediaSearchQuery.collectAsState()

    val context = LocalContext.current


    var customNotiTitle by remember { mutableStateOf("\ud83d\ude80 AppsHub System Update") }
    var customNotiBody by remember { mutableStateOf("New live streams & sports channels are now available!") }
    var customUserEmailInput by remember { mutableStateOf("") }
    var userSearchQuery by remember { mutableStateOf("") }
    var selectedUserFilter by remember { mutableIntStateOf(0) } // 0: All, 1: Active, 2: Restricted
    var selectedLogFilter by remember { mutableStateOf("ALL") } // ALL, ERROR, ALERT, INFO
    var selectedTab by remember { mutableStateOf<Int?>(null) }

    val totalPlaylistsCount = remember(playlistsState) {
        if (playlistsState is UiState.Success) (playlistsState as UiState.Success).data.size else 0
    }

    val filteredRegisteredUsers = remember(registeredUsers, userSearchQuery, selectedUserFilter) {
        registeredUsers.filter { user ->
            val matchesQuery = userSearchQuery.isBlank() || user.email.contains(userSearchQuery, ignoreCase = true)
            val matchesFilter = when (selectedUserFilter) {
                1 -> !user.isRestricted
                2 -> user.isRestricted
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    val filteredLogs = remember(logs, selectedLogFilter) {
        if (selectedLogFilter == "ALL") logs
        else logs.filter { it.type.equals(selectedLogFilter, ignoreCase = true) }
    }

    val isAuthorized = userProfile?.isSuperAdmin == true

    LaunchedEffect(Unit) {
        if (!isAuthorized) {
            viewModel.logUnauthorizedAccessAttempt(accessedScreen = "Super Admin Terminal")
        }
    }

    // Modern Deep Space Black Canvas
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
    ) {
        if (!isAuthorized) {
            // High Tech Intruder / Lockdown View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(NeonMagenta.copy(alpha = 0.12f))
                        .border(2.dp, NeonMagenta, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = NeonMagenta,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "SUPER ADMIN TERMINAL LOCKED",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
                    color = NeonMagenta,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = DeepSlate,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Developer Context: ${userProfile?.email ?: "Guest Streamer"}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Access is restricted to authorized developer credentials. This attempt has been logged in system telemetry logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Real Authorized Super Admin Dashboard
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpaceBlack)
            ) {
                AdminHeader(
                    userProfile = userProfile,
                    telemetry = telemetry,
                    selectedTab = selectedTab,
                    onBack = { selectedTab = null }
                )

                if (selectedTab == null) {
                    AdminGridDashboard(onTabSelected = { selectedTab = it })
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> AdminTelemetryTab(telemetry = telemetry, totalPlaylistsCount = totalPlaylistsCount)
                            1 -> AdminUsersTab(
                                searchQuery = userSearchQuery,
                                onSearchChange = { userSearchQuery = it },
                                selectedFilter = selectedUserFilter,
                                onFilterSelect = { selectedUserFilter = it },
                                customEmailInput = customUserEmailInput,
                                onEmailInputChange = { customUserEmailInput = it },
                                onAddOrToggleUser = { email ->
                                    if (email.isNotBlank()) {
                                        val trimmed = email.trim()
                                        viewModel.toggleUserRestriction(trimmed, true)
                                        Toast.makeText(context, "Restricted account $trimmed", Toast.LENGTH_SHORT).show()
                                        customUserEmailInput = ""
                                    }
                                },
                                registeredUsers = filteredRegisteredUsers,
                                onToggleBlock = { accEmail, currentStatus ->
                                    viewModel.toggleUserRestriction(accEmail, !currentStatus)
                                    val msg = if (!currentStatus) "Restricted access: $accEmail" else "Restored access: $accEmail"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                            2 -> AdminBroadcastTab(
                                customTitle = customNotiTitle,
                                onTitleChange = { customNotiTitle = it },
                                customBody = customNotiBody,
                                onBodyChange = { customNotiBody = it },
                                onSend = { title, body ->
                                    if (title.isNotBlank() && body.isNotBlank()) {
                                        viewModel.sendBroadcastNotification(context, title, body)
                                        Toast.makeText(context, "Broadcast Push Sent!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Please enter title & message", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            3 -> AdminContentTab()
                            4 -> AdminPremiumTab(
                                mediaState = mediaState,
                                searchQuery = mediaSearchQuery,
                                onSearch = { viewModel.setMediaSearchQuery(it) },
                                premiumMedia = premiumMedia,
                                onTogglePremium = { id -> viewModel.togglePremiumMedia(id) }
                            )
                            5 -> AdminSettingsTab(viewModel = viewModel)
                            6 -> AdminLogsTab(
                                logs = filteredLogs,
                                selectedFilter = selectedLogFilter,
                                onFilterSelect = { selectedLogFilter = it },
                                onClearLogs = {
                                    viewModel.clearAdminLogs()
                                    Toast.makeText(context, "Logs Cleared", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminGridDashboard(
    onTabSelected: (Int) -> Unit
) {
    val menuItems = listOf(
        Pair("DASHBOARD", Icons.Default.Dashboard),
        Pair("USER MANAGEMENT", Icons.Default.People),
        Pair("NOTIFICATION HUB", Icons.Default.Notifications),
        Pair("CONTENT MANAGEMENT", Icons.Default.Folder),
        Pair("PREMIUM ACCESS CONTROL", Icons.Default.Star),
        Pair("SYSTEM SETTINGS", Icons.Default.Settings),
        Pair("ACTIVITY LOGS", Icons.Default.List)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(menuItems) { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF2A2A35), RoundedCornerShape(16.dp))
                    .clickable { onTabSelected(index) }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = item.second,
                    contentDescription = item.first,
                    tint = Color(0xFFFF6D00),
                    modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                )
                Text(
                    text = item.first,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AdminHeader(
    userProfile: com.example.ui.viewmodel.UserProfile?,
    telemetry: TelemetryStats,
    selectedTab: Int?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedTab != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(
                    text = "ADMIN CONTROL CENTER",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
                Text(
                    text = "Click a button to open an individual section page here.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color(0xFFA0A0B0)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Alerts",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6D00)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AdminContentTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Media Library & EPG Management coming soon.", color = Color.Gray)
    }
}

@Composable
fun AdminPremiumTab(
    mediaState: com.example.ui.viewmodel.UiState<List<com.example.data.model.MediaItem>>,
    searchQuery: String,
    onSearch: (String) -> Unit,
    premiumMedia: List<com.example.data.database.PremiumMediaEntity>,
    onTogglePremium: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearch,
            placeholder = { Text("Search media to mark as Premium...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        when (mediaState) {
            is com.example.ui.viewmodel.UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
            is com.example.ui.viewmodel.UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error loading media", color = NeonMagenta)
                }
            }
            is com.example.ui.viewmodel.UiState.Success -> {
                val itemsList = mediaState.data
                PreloadImages(urls = itemsList.map { it.imageUrl })
                if (itemsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No media found. Try searching.", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(itemsList, key = { it.id }) { item ->
                            val isPremium = premiumMedia.any { it.id == item.id }
                            Surface(
                                color = DeepSlate,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isPremium) Color(0xFFFFD700) else BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        ShimmerAsyncImage(
                                            model = item.imageUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(50.dp, 75.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                        Column {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.category,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextSecondary
                                            )
                                            if (isPremium) {
                                                Text(
                                                    text = "PREMIUM",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFFFD700)
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { onTogglePremium(item.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPremium) DeepSlate else NeonCyan
                                        ),
                                        border = if (isPremium) BorderStroke(1.dp, Color(0xFFFFD700)) else null,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isPremium) "Remove Premium" else "Mark Premium",
                                            color = if (isPremium) Color(0xFFFFD700) else Color.Black,
                                            fontWeight = FontWeight.Bold,
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
}

@Composable
fun AdminSettingsTab(viewModel: StreamViewModel) {
    val context = LocalContext.current
    val appControlConfig by viewModel.appControlConfig.collectAsState()
    val initialAd = appControlConfig?.launchAdOverlay

    var enabled by remember(initialAd) { mutableStateOf(initialAd?.enabled ?: false) }
    var mediaType by remember(initialAd) { mutableStateOf(initialAd?.mediaType ?: "auto") }
    var mediaUrl by remember(initialAd) { mutableStateOf(initialAd?.mediaUrl ?: "") }
    var targetUrl by remember(initialAd) { mutableStateOf(initialAd?.targetUrl ?: "") }
    var title by remember(initialAd) { mutableStateOf(initialAd?.title ?: "") }
    var description by remember(initialAd) { mutableStateOf(initialAd?.description ?: "") }
    var buttonText by remember(initialAd) { mutableStateOf(initialAd?.buttonText ?: "Learn More") }
    var skipDuration by remember(initialAd) { mutableStateOf((initialAd?.skipDurationSeconds ?: 5).toString()) }
    var displayFrequency by remember(initialAd) { mutableStateOf(initialAd?.displayFrequency ?: "ONCE_AFTER_INSTALL") }
    var isSaving by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pre-Splash Fullscreen Ad / Poster",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Shows full-screen video or poster on app launch before lock & splash",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = SpaceBlack
                            )
                        )
                    }

                    HorizontalDivider(color = BorderColor, thickness = 1.dp)

                    // Media Type Selector
                    Text("Media Type", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("auto" to "Auto-Detect", "video" to "Video (MP4/HLS)", "image" to "Poster / Image").forEach { (typeKey, label) ->
                            val isSelected = mediaType.equals(typeKey, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { mediaType = typeKey },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = SpaceBlack,
                                    labelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) NeonCyan else BorderColor
                                )
                            )
                        }
                    }

                    // Media URL Input
                    OutlinedTextField(
                        value = mediaUrl,
                        onValueChange = { mediaUrl = it },
                        label = { Text("Media URL (MP4 / M3U8 / JPG / PNG / GIF)", color = TextSecondary) },
                        placeholder = { Text("https://example.com/ad_video.mp4", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color(0xFF38BDF8),
                            unfocusedTextColor = Color(0xFF38BDF8),
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Target / CTA URL Input
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = { Text("Action / Target Click URL (Optional)", color = TextSecondary) },
                        placeholder = { Text("https://t.me/mychannel or website URL", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color(0xFF38BDF8),
                            unfocusedTextColor = Color(0xFF38BDF8),
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Title & Description
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Ad Title", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color(0xFF38BDF8),
                                unfocusedTextColor = Color(0xFF38BDF8),
                                cursorColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = buttonText,
                            onValueChange = { buttonText = it },
                            label = { Text("Button Text", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color(0xFF38BDF8),
                                unfocusedTextColor = Color(0xFF38BDF8),
                                cursorColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Ad Subtitle / Message", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color(0xFF38BDF8),
                            unfocusedTextColor = Color(0xFF38BDF8),
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Skip duration & Display Frequency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = skipDuration,
                            onValueChange = { if (it.all { char -> char.isDigit() }) skipDuration = it },
                            label = { Text("Skip After (Sec)", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color(0xFF38BDF8),
                                unfocusedTextColor = Color(0xFF38BDF8),
                                cursorColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("Display Frequency", color = TextSecondary, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val isOnce = displayFrequency.equals("ONCE_AFTER_INSTALL", ignoreCase = true)
                                FilterChip(
                                    selected = !isOnce,
                                    onClick = { displayFrequency = "EVERY_LAUNCH" },
                                    label = { Text("Every Launch", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonCyan,
                                        containerColor = SpaceBlack,
                                        labelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = isOnce,
                                    onClick = { displayFrequency = "ONCE_AFTER_INSTALL" },
                                    label = { Text("Once", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonCyan,
                                        containerColor = SpaceBlack,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Action Buttons: Preview & Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val testConfig = com.example.ui.viewmodel.LaunchAdOverlayConfig(
                                    enabled = true,
                                    mediaType = mediaType,
                                    mediaUrl = mediaUrl.ifBlank { "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
                                    targetUrl = targetUrl,
                                    title = title.ifBlank { "Sample Launch Ad" },
                                    description = description.ifBlank { "This is a live preview of your launch advertisement." },
                                    buttonText = buttonText.ifBlank { "Learn More" },
                                    skipDurationSeconds = skipDuration.toIntOrNull() ?: 5,
                                    displayFrequency = displayFrequency
                                )
                                viewModel.previewLaunchAd(testConfig)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = BorderStroke(1.dp, NeonCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview Ad")
                        }

                        Button(
                            onClick = {
                                isSaving = true
                                val newConfig = com.example.ui.viewmodel.LaunchAdOverlayConfig(
                                    enabled = enabled,
                                    mediaType = mediaType,
                                    mediaUrl = mediaUrl.trim(),
                                    targetUrl = targetUrl.trim(),
                                    title = title.trim(),
                                    description = description.trim(),
                                    buttonText = buttonText.trim().ifBlank { "Learn More" },
                                    skipDurationSeconds = skipDuration.toIntOrNull() ?: 5,
                                    displayFrequency = displayFrequency,
                                    adId = "ad_${System.currentTimeMillis()}"
                                )
                                viewModel.saveLaunchAdOverlayConfig(newConfig) { success, msg ->
                                    isSaving = false
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SpaceBlack, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = SpaceBlack)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save & Publish", color = SpaceBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTelemetryTab(
    telemetry: TelemetryStats,
    totalPlaylistsCount: Int
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Section Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME METRICS & PLATFORM HEALTH",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = NeonCyan
                )
                Text(
                    text = "Pulsing Live  Auto Sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // 2x2 Grid Stats Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminMetricCard(
                        title = "Live Concurrent Viewers",
                        value = "%,d".format(telemetry.liveUsers),
                        subLabel = "+12.4% vs last hour",
                        icon = Icons.Default.People,
                        accentColor = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    AdminMetricCard(
                        title = "Visited Real Users",
                        value = "%,d".format(telemetry.visitedRealUsers),
                        subLabel = "+2,840 today",
                        icon = Icons.Default.VerifiedUser,
                        accentColor = NeonPurple,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminMetricCard(
                        title = "Active Playlists",
                        value = if (totalPlaylistsCount > 0) "$totalPlaylistsCount Playlists" else "Active (Real)",
                        subLabel = "100% Operational",
                        icon = Icons.Default.PlaylistPlay,
                        accentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    AdminMetricCard(
                        title = "Security Intrusions",
                        value = "${telemetry.unauthorizedAccessAttempts} Blocked",
                        subLabel = "Firewall Shield Active",
                        icon = Icons.Default.Shield,
                        accentColor = NeonMagenta,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Global CDN Node Status Map
        item {
            Surface(
                color = DeepSlate,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EDGE CDN NODES & LATENCY MAP",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "4 ONLINE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFF10B981))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val nodes = listOf(
                        Triple("US-East (Virginia)", "18 ms", Color(0xFF10B981)),
                        Triple("EU-Central (Frankfurt)", "32 ms", Color(0xFF10B981)),
                        Triple("AP-South (Mumbai)", "45 ms", Color(0xFF10B981)),
                        Triple("SA-East (So Paulo)", "82 ms", FlameGold)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        nodes.forEach { (nodeName, ping, statusColor) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SpaceBlack)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                                    Text(text = nodeName, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                                Text(text = ping, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = NeonCyan)
                            }
                        }
                    }
                }
            }
        }

        // Server Load & Resource Allocation Gauges
        item {
            Surface(
                color = DeepSlate,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HARDWARE & NETWORK TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    TelemetryProgressRow(label = "Primary CDN Node CPU Load", percentage = telemetry.cpuUsage)
                    Spacer(modifier = Modifier.height(10.dp))
                    TelemetryProgressRow(label = "Edge Memory Buffer Allocation", percentage = telemetry.memoryUsage)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "DNS Resolution Latency", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(text = "${telemetry.cdnResponseMs} ms", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = NeonCyan)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Bandwidth Throughput", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(text = "312.4 Gbps", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF10B981))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminMetricCard(
    title: String,
    value: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DeepSlate,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AdminBroadcastTab(
    customTitle: String,
    onTitleChange: (String) -> Unit,
    customBody: String,
    onBodyChange: (String) -> Unit,
    onSend: (String, String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                color = DeepSlate,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.2.dp, NeonPurple.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonPurple.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Broadcast",
                                tint = NeonPurple,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "PUSH NOTIFICATION BROADCASTER",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Send real-time alerts directly to device system panels",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = onTitleChange,
                        label = { Text("Notification Title", color = TextSecondary, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customBody,
                        onValueChange = onBodyChange,
                        label = { Text("Notification Message Body", color = TextSecondary, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onTitleChange("\ud83d\ude80 System Update Available")
                                    onBodyChange("Home Air TV v2.5 update is ready with fast stream playback.")
                                },
                                label = { Text("\ud83d\ude80 Update", fontSize = 11.sp, color = NeonCyan) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onTitleChange(" Scheduled Maintenance")
                                    onBodyChange("Server maintenance tonight at 02:00 AM UTC for 15 mins.")
                                },
                                label = { Text(" Maintenance", fontSize = 11.sp, color = FlameGold) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onTitleChange(" New Live Channels Added!")
                                    onBodyChange("Added 50+ new FHD sports & movie channels to Home Air TV.")
                                },
                                label = { Text(" New Channels", fontSize = 11.sp, color = Color(0xFF10B981)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onTitleChange("\ud83c\udf89 Live Sports Broadcast Starting!")
                                    onBodyChange("The match is live right now! Tune in on Home Air TV.")
                                },
                                label = { Text("\ud83c\udf89 Live Sports", fontSize = 11.sp, color = NeonMagenta) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Smartphone Notification Banner Preview Card
                    Surface(
                        color = SpaceBlack,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(imageVector = Icons.Default.Tv, contentDescription = "App Icon", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                    Text(text = "Home Air TV  Just now", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
                                }
                                Text(text = "PREVIEW", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = NeonPurple)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (customTitle.isBlank()) "Title Preview" else customTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (customBody.isBlank()) "Message preview content..." else customBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { onSend(customTitle, customBody) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Broadcast Push Notification to Device",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUsersTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: Int,
    onFilterSelect: (Int) -> Unit,
    customEmailInput: String,
    onEmailInputChange: (String) -> Unit,
    onAddOrToggleUser: (String) -> Unit,
    registeredUsers: List<com.example.data.database.RegisteredUserEntity>,
    onToggleBlock: (String, Boolean) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Quick Block / Restrict Box
        item {
            Surface(
                color = DeepSlate,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MANUALLY BLOCK OR RESTRICT ACCOUNT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customEmailInput,
                            onValueChange = onEmailInputChange,
                            placeholder = { Text("user.email@gmail.com", color = TextSecondary, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        )

                        Button(
                            onClick = { onAddOrToggleUser(customEmailInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Restrict", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Search & Filter Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search registered accounts...", color = TextSecondary, fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val filters = listOf("All Accounts", "Active Only", "Restricted Only")
                    filters.forEachIndexed { index, label ->
                        val isSelected = selectedFilter == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterSelect(index) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = DeepSlate,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Users Account List
        if (registeredUsers.isEmpty()) {
            item {
                Surface(
                    color = DeepSlate,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No user accounts match your search filter.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(registeredUsers, key = { it.email }) { user ->
                val isBlocked = user.isRestricted
                Surface(
                    color = DeepSlate,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (isBlocked) NeonMagenta.copy(alpha = 0.5f) else BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isBlocked) NeonMagenta.copy(alpha = 0.18f) else Color(0xFF10B981).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.email.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = if (isBlocked) NeonMagenta else Color(0xFF10B981)
                                )
                            }

                            Column {
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isBlocked) NeonMagenta else Color(0xFF10B981))
                                    )
                                    Text(
                                        text = if (isBlocked) "RESTRICTED / BLOCKED" else "ACTIVE STREAMER",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = if (isBlocked) NeonMagenta else Color(0xFF10B981)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onToggleBlock(user.email, isBlocked) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBlocked) Color(0xFF10B981) else NeonMagenta
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = if (isBlocked) "Restore" else "Restrict",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminLogsTab(
    logs: List<AdminLogEntity>,
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Filter Row & Action Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    val filters = listOf("ALL", "ERROR", "ALERT", "INFO")
                    items(filters, key = { it }) { category ->
                        val isSelected = selectedFilter == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterSelect(category) },
                            label = { Text(category, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = DeepSlate,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClearLogs) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear", tint = NeonMagenta, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs", color = NeonMagenta, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Surface(
                    color = DeepSlate,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Clear", tint = Color(0xFF10B981), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "All Systems Nominal", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "No diagnostic errors or security warnings recorded.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                AdminLogCard(log)
            }
        }
    }
}

@Composable
fun TelemetryMetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DeepSlate,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = accentColor
            )
        }
    }
}

@Composable
fun TelemetryProgressRow(label: String, percentage: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text(text = "$percentage%", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = percentage / 100f,
            color = if (percentage > 70) NeonMagenta else NeonCyan,
            trackColor = SpaceBlack,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun AdminLogCard(log: AdminLogEntity) {
    Surface(
        color = DeepSlate,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 0.8.dp,
            color = when (log.type) {
                "ALERT" -> FlameGold.copy(alpha = 0.6f)
                "ERROR" -> NeonMagenta.copy(alpha = 0.6f)
                else -> NeonCyan.copy(alpha = 0.3f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (log.type) {
                            "ALERT" -> FlameGold.copy(alpha = 0.15f)
                            "ERROR" -> NeonMagenta.copy(alpha = 0.15f)
                            else -> NeonCyan.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (log.type) {
                        "ALERT" -> Icons.Default.Warning
                        "ERROR" -> Icons.Default.Report
                        else -> Icons.Default.Info
                    },
                    contentDescription = log.type,
                    tint = when (log.type) {
                        "ALERT" -> FlameGold
                        "ERROR" -> NeonMagenta
                        else -> NeonCyan
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = log.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)),
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = log.message, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}


@Composable
fun SettingsDropdownRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Full width icon, title and subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f))
                        .border(1.dp, iconTint.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown Selector Button directly BELOW the title name
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(14.dp),
                    color = CyberGray,
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = options.getOrElse(selectedIndex) { "" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = iconTint
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .background(DeepSlate)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (index == selectedIndex) FontWeight.ExtraBold else FontWeight.Normal
                                    ),
                                    color = if (index == selectedIndex) iconTint else TextPrimary
                                )
                            },
                            onClick = {
                                onOptionSelected(index)
                                expanded = false
                            },
                            leadingIcon = if (index == selectedIndex) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

fun saveUriToOfflineStorage(context: android.content.Context, uri: Uri): java.io.File? {
    return try {
        val resolver = context.contentResolver
        var displayName: String? = null
        try {
            val cursor = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        displayName = it.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}

        val type = resolver.getType(uri)
        val extension = if (type != null && type.contains("video/")) {
            type.substringAfter("video/")
        } else {
            "mp4"
        }

        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()

        val fileName = if (!displayName.isNullOrBlank()) {
            displayName!!
        } else {
            "offline_video_${System.currentTimeMillis()}.$extension"
        }

        val targetFile = java.io.File(dir, fileName)
        resolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        targetFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ==========================================
// 6. SETTINGS SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit = {},
    isHeaderVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isInPipMode by viewModel.isInPipMode.collectAsState()
    val profile = userProfile
    val context = LocalContext.current


    val scope = rememberCoroutineScope()

    val isPremium by SubscriptionManager.isPremium.collectAsState()
    val isRedeemActive by viewModel.isRedeemActive.collectAsState()
    val isExpired by SubscriptionManager.isExpired.collectAsState()
    val isLifetime = SubscriptionManager.isLifetimeUser(profile?.email)
    val effectiveIsPremium = viewModel.isUserPremium(profile?.email)
    val redeemPlanName = if (isRedeemActive) viewModel.getRedeemPlanName() else ""

    var showBottomNavCustomizerSheet by remember { mutableStateOf(false) }
    val browseSlotType by viewModel.browseSlotType.collectAsState()
    val airSlotType by viewModel.airSlotType.collectAsState()
    val downloadsSlotType by viewModel.downloadsSlotType.collectAsState()

    var showSignInSheetInSettings by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showAiringReelsScreen by remember { mutableStateOf(false) }
    var showDiscoverFeedsScreen by remember { mutableStateOf(false) }
    var showWatchHistorySheet by remember { mutableStateOf(false) }
    var showWebVersionView by remember { mutableStateOf(false) }
    var showMasterAnimeBrowser by remember { mutableStateOf(false) }
    var showCineStreamBrowser by remember { mutableStateOf(false) }
    var showSubscriptionSheet by remember { mutableStateOf(false) }
    var showSubscriptionPlanModalInProfile by remember { mutableStateOf(false) }
    var showHelpCenterSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showAdvancedSettingsSheet by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showPrivacyTermsSheet by remember { mutableStateOf(false) }
    var showCopyrightSheet by remember { mutableStateOf(false) }
    var showFloatingPlayerLimitSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var showAboutUsSheet by remember { mutableStateOf(false) }
    val maxFloatingPlayers by viewModel.maxFloatingPlayers.collectAsState()
    val showDownloadLibrary by viewModel.showDownloadLibraryGlobal.collectAsState()

    var activeSubPage by remember { mutableStateOf("MAIN") }
    val selectedQueueVideos = remember { mutableStateListOf<java.io.File>() }

    var showLocalVideosPicker by remember { mutableStateOf(false) }
    var selectedLocalVideoFile by remember { mutableStateOf<java.io.File?>(null) }
    
    var showM3UManagerSheet by remember { mutableStateOf(false) }
    var showArchiveChannelsSheet by remember { mutableStateOf(false) }

    var m3uFileNameInput by remember { mutableStateOf("") }
    var m3uUrlInput by remember { mutableStateOf("") }

    val multipleVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val savedList = mutableListOf<java.io.File>()
            uris.forEach { uri ->
                val savedFile = saveUriToOfflineStorage(context, uri)
                if (savedFile != null && savedFile.exists()) {
                    savedList.add(savedFile)
                }
            }
            if (savedList.isNotEmpty()) {
                if (savedList.size == 1) {
                    // Single video selected: Direct Play
                    selectedLocalVideoFile = savedList.first()
                    Toast.makeText(context, "Playing selected video", Toast.LENGTH_SHORT).show()
                } else {
                    // Multiple videos selected: Import then Play
                    selectedQueueVideos.clear()
                    selectedQueueVideos.addAll(savedList)
                    selectedLocalVideoFile = savedList.first()
                    Toast.makeText(context, "Imported ${savedList.size} videos to Offline Playlist Library!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Failed to import video file(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedFile = saveUriToOfflineStorage(context, uri)
            if (savedFile != null && savedFile.exists()) {
                selectedLocalVideoFile = savedFile
                Toast.makeText(context, "Playing video", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load local video file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(showLocalVideosPicker) {
        if (showLocalVideosPicker) {
            videoPickerLauncher.launch("video/*")
            showLocalVideosPicker = false
        }
    }

    val m3uFilePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank() && (text.contains("#EXTM3U") || text.contains("#EXTINF"))) {
                    val finalName = m3uFileNameInput.ifBlank { "Custom Playlist File" }
                    viewModel.addCustomPlaylist(finalName, text, "file", uri.toString())
                    Toast.makeText(context, "M3U File imported successfully!", Toast.LENGTH_SHORT).show()
                    m3uFileNameInput = ""
                } else {
                    Toast.makeText(context, "Invalid M3U file format", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading M3U: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentVersionName = remember { AppUpdateManager.getAppVersionName(context) }
    val currentVersionCode = remember { AppUpdateManager.getAppVersionCode(context) }
    val updateInfoState by viewModel.updateInfoState.collectAsState()
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<String?>(null) }

    // Dropdown States
    val selectedBufferIndex by viewModel.bufferIndex.collectAsState()
    val bufferOptions = remember {
        listOf(
            "Ultra Low Latency (2s) - Fastest Start",
            "Medium Buffer (5s) - Recommended for Live TV",
            "Large Buffer (10s) - Anti-Freeze & Heavy Traffic",
            "Maximum Anti-Buffer (25s) - Zero Lag / Weak Net"
        )
    }

    val selectedDecoderIndex by viewModel.decoderIndex.collectAsState()
    val decoderOptions = remember {
        listOf(
            "HW+ Hardware Accelerated (GPU Ultra Smooth)",
            "Standard Hardware Decoder",
            "Software Decoder (Fallback Compatibility)",
            "Auto Smart-Adaptive Engine"
        )
    }

    val selectedQualityIndex by viewModel.qualityIndex.collectAsState()
    val qualityOptions = remember {
        listOf("Auto (Best Available)", "4K Ultra HD (2160p)", "Full HD (1080p)", "HD Ready (720p)", "Data Saver (480p)")
    }

    val selectedAudioIndex by viewModel.audioIndex.collectAsState()
    val audioOptions = remember {
        listOf(
            "Default System",
            "English (US/UK)",
            "Bengali ()",
            "Spanish (Espaol)",
            "Hindi ()",
            "Japanese ()",
            "French (Franais)",
            "German (Deutsch)",
            "Arabic ()"
        )
    }

    val selectedThemeIndex by viewModel.themeIndex.collectAsState()
    val themeOptions = remember {
        listOf("Neon Cyber Glow", "AMOLED Midnight Black", "Cosmic Purple", "Emerald Wave")
    }

    // Toggle States
    val isHardwareAccel by viewModel.hardwareAccel.collectAsState()
    val isAutoPip by viewModel.autoPip.collectAsState()
    val isLowLatencySync by viewModel.lowLatencySync.collectAsState()

    // Real TMDB Banner Media
    val mediaState by viewModel.mediaState.collectAsState()
    val realMediaList = remember(mediaState) {
        ((mediaState as? UiState.Success<*>)?.data as? List<*>)?.filterIsInstance<MediaItem>() ?: emptyList()
    }

    PreloadImages(urls = realMediaList.map { it.imageUrl })

    val bannerMediaList = remember(realMediaList) {
        if (realMediaList.isNotEmpty()) {
            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            realMediaList.shuffled(kotlin.random.Random(today)).take(6)
        } else emptyList()
    }

    var bannerIndex by remember { mutableIntStateOf(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 1000,
        pageCount = { if (bannerMediaList.isNotEmpty()) Int.MAX_VALUE else 1 }
    )
    LaunchedEffect(bannerMediaList.size) {
        if (bannerMediaList.isNotEmpty()) {
            while (true) {
                kotlinx.coroutines.delay(4000)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage + 1,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }
    LaunchedEffect(pagerState.currentPage, bannerMediaList.size) {
        if (bannerMediaList.isNotEmpty()) {
            bannerIndex = pagerState.currentPage % bannerMediaList.size
        }
    }
    val currentBannerItem = remember(bannerMediaList, bannerIndex) {
        if (bannerMediaList.isNotEmpty()) bannerMediaList.getOrNull(bannerIndex) else null
    }

    var searchQuery by remember { mutableStateOf("") }
    var activeWebPlayer by remember { mutableStateOf<WebPlayerState?>(null) }

    androidx.activity.compose.BackHandler(enabled = activeWebPlayer != null || showDownloadLibrary) {
        if (activeWebPlayer != null) {
            activeWebPlayer = null
        } else if (showDownloadLibrary) {
            viewModel.setShowDownloadLibraryGlobal(false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.tabReselectEvent.collect { tabIndex ->
            if (tabIndex == 0 || tabIndex == 1 || tabIndex == 3) {
                activeWebPlayer = null
                viewModel.setShowDownloadLibraryGlobal(false)
            }
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val settingsBgColor = if (isDark) SpaceBlack else Color(0xFFF5F5F7)
    val settingsCardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val settingsSearchBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE8E8ED)
    val settingsTextColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val settingsSubTextColor = if (isDark) Color(0xFFA1A1A6) else Color(0xFF8E8E93)
    val settingsBorderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    var hubClickCount by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            hubClickCount++
                        }
                    }
                }
            }
    ) {
        if (showDownloadLibrary) {
            DownloadLibraryScreen(
                viewModel = viewModel,
                onBack = { viewModel.setShowDownloadLibraryGlobal(false) }
            )
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(settingsBgColor)
                    .then(if (activeWebPlayer == null) Modifier.statusBarsPadding() else Modifier)
                    .padding(top = 10.dp)
            ) {
                if (activeSubPage == "ARCHIVED_CHANNELS") {
                    ArchivedChannelsSubPage(viewModel = viewModel, onBack = { activeSubPage = "MAIN" })
                } else if (activeSubPage == "M3U8_MANAGER") {
                    M3uPlaylistsManagerSubPage(
                        viewModel = viewModel,
                        onBack = { activeSubPage = "MAIN" },
                        onNavigateToAirTab = onNavigateToPlayer,
                        m3uFileLauncher = m3uFilePickerLauncher,
                        nameInput = m3uFileNameInput,
                        onNameChange = { m3uFileNameInput = it },
                        urlInput = m3uUrlInput,
                        onUrlChange = { m3uUrlInput = it }
                    )
                } else if (activeSubPage == "LOCAL_VIDEOS_MANAGER") {
                    LocalVideosManagerSubPage(
                        viewModel = viewModel,
                        onBack = { activeSubPage = "MAIN" },
                        multiPickerLauncher = multipleVideoPickerLauncher,
                        selectedQueueVideos = selectedQueueVideos,
                        onPlayFile = { selectedLocalVideoFile = it }
                    )
                } else {
                    // Top Search Bar removed as requested

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {

            // 2. Home Air TV Branding Row (Matching Screenshot)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeAirTvBrandingHeader(selectedPlaylistName = null, clickTrigger = hubClickCount)

                    // Sign In Button Pill
                    Surface(
                        onClick = {
                            if (profile != null) {
                                showProfileSheet = true
                            } else {
                                showSignInSheetInSettings = true
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = settingsCardBg,
                        border = BorderStroke(1.dp, settingsBorderColor),
                        shadowElevation = 2.dp,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF6B00)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profile?.avatarUrl != null && profile.avatarUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = profile.avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Sign In",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (profile != null) profile.name.take(8) else "Sign In",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = settingsTextColor
                            )
                        }
                    }
                }
            }

            // 3. Watch Anything Hero Banner (Matching Screenshot)
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(178.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "\u2605 ${currentBannerItem?.rating ?: "8.5"} Rating",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    ),
                                    color = if (isDark) Color.White else Color(0xFF1C1C1E)
                                )
                            }

                            Text(
                                text = currentBannerItem?.title ?: "Masters of the Universe",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B00),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    currentBannerItem?.let { item ->
                                        viewModel.playMediaItem(item, 1, 1)
                                        activeWebPlayer = WebPlayerState(item, 1, 1)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Watch Now",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Watch Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Right Side Artwork (Classic Horizontal Camera Film Strip with Top & Bottom Perforations)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7),
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Top & Bottom Camera Film Sprocket Perforations
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(7.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(8) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 6.dp, height = 4.dp)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(if (isDark) Color(0xFF0A0A0C) else Color(0xFFD1D1D6))
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(7.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(8) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 6.dp, height = 4.dp)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(if (isDark) Color(0xFF0A0A0C) else Color(0xFFD1D1D6))
                                            )
                                        }
                                    }
                                }

                                // Wavy Camera Film Reel Carousel (Dual Grid: Right = Vivid Color, Left = Grey Wavy Curve)
                                androidx.compose.foundation.pager.HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp, vertical = 7.dp),
                                    contentPadding = PaddingValues(start = 68.dp, end = 2.dp),
                                    pageSpacing = (-6).dp,
                                    userScrollEnabled = true
                                ) { page ->
                                    val actualIndex = if (bannerMediaList.isNotEmpty()) page % bannerMediaList.size else 0
                                    val bannerItem = bannerMediaList.getOrNull(actualIndex) ?: currentBannerItem

                                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                    val progress = pageOffset.coerceIn(0f, 1f)

                                    // 1. Color desaturation: 1f (Full Color on Right Grid) -> 0f (Grey / Grayscale on Left Grid)
                                    val saturation = (1f - progress).coerceIn(0f, 1f)
                                    val colorMatrix = if (saturation < 0.98f) {
                                        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation) }
                                    } else null

                                    // 2. Wavy Camera Roll Strip Curve Transformation for Left Grid
                                    val scale = androidx.compose.ui.util.lerp(1f, 0.82f, progress)
                                    val alpha = androidx.compose.ui.util.lerp(1f, 0.72f, progress)
                                    val rotationZ = androidx.compose.ui.util.lerp(0f, -8f, progress)
                                    val translationY = androidx.compose.ui.util.lerp(0f, 12f, progress)

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1C1C1E),
                                        border = BorderStroke(1.2.dp, if (progress < 0.3f) Color(0xFFFF6B00) else Color(0xFF333338)),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                this.alpha = alpha
                                                this.rotationZ = rotationZ
                                                this.translationY = translationY
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bannerItem != null && bannerItem.imageUrl.isNotEmpty()) {
                                                AsyncImage(
                                                    model = bannerItem.imageUrl,
                                                    contentDescription = bannerItem.title,
                                                    contentScale = ContentScale.Crop,
                                                    colorFilter = colorMatrix?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(it) },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(
                                                                    Color.Transparent,
                                                                    Color.Black.copy(alpha = 0.6f)
                                                                )
                                                            )
                                                        )
                                                )

                                                // Play Icon Badge on Active Right Frame
                                                if (progress < 0.3f) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFFF6B00).copy(alpha = 0.9f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Play",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C2E)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tv,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.9f),
                                                        modifier = Modifier.size(24.dp)
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

            // 4. Air Settings Section Header (Matching Screenshot)
            item {
                Text(
                    text = "Air Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = settingsTextColor,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            item {
                SecretSettingRow(
                    title = "Configure Tabs",
                    subtitle = "Customize bottom navigation layout",
                    icon = Icons.Default.DashboardCustomize,
                    onClick = { showBottomNavCustomizerSheet = true }
                )
            }

            // 5. Menu Items List (Exact visual layout from Screenshot)
            item {
                SecretSettingRow(
                    title = com.example.ui.theme.AppTranslation.getString("profile", selectedAudioIndex),
                    icon = Icons.Outlined.Person,
                    onClick = {
                        if (userProfile != null) {
                            showProfileSheet = true
                        } else {
                            showSignInSheetInSettings = true
                        }
                    }
                )
            }

            item {
                SecretSettingRow(
                    title = "Airing Reels",
                    subtitle = "Watch curated short videos and anime reels",
                    icon = Icons.Default.Movie,
                    onClick = { showAiringReelsScreen = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Feeds",
                    subtitle = "Watch trending & latest discover video stream",
                    icon = Icons.Default.DynamicFeed,
                    onClick = { showDiscoverFeedsScreen = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Subscription",
                    icon = Icons.Outlined.Tv,
                    onClick = { showSubscriptionSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Master Anime",
                    subtitle = "Stream exclusive anime collection",
                    icon = Icons.Outlined.PlayCircle,
                    onClick = { showMasterAnimeBrowser = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Cine Stream",
                    subtitle = "Stream movies & cinema collection (cine.hmair.xyz)",
                    icon = Icons.Outlined.Movie,
                    onClick = { showCineStreamBrowser = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Watch History",
                    subtitle = "View recently watched movies, shows & live TV",
                    icon = Icons.Outlined.History,
                    onClick = { showWatchHistorySheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Visit Web Version",
                    subtitle = "Open homeairtv.xubilaswebdevcorp.shop inside the app",
                    icon = Icons.Outlined.Language,
                    onClick = { showWebVersionView = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "My Downloads",
                    subtitle = "Offline videos library",
                    icon = Icons.Outlined.Download,
                    onClick = { viewModel.setShowDownloadLibraryGlobal(true) }
                )
            }

            item {
                SecretSettingRow(
                    title = "Archived Channels",
                    subtitle = "Manage hidden or disabled live TV channels",
                    icon = Icons.Outlined.Archive,
                    onClick = { activeSubPage = "ARCHIVED_CHANNELS" }
                )
            }

            item {
                SecretSettingRow(
                    title = "Local Offline Videos",
                    subtitle = "Play your offline and local video files",
                    icon = Icons.Outlined.VideoLibrary,
                    onClick = { activeSubPage = "LOCAL_VIDEOS_MANAGER" }
                )
            }

            item {
                SecretSettingRow(
                    title = "M3U8 Player & Playlists",
                    subtitle = "Import and manage custom M3U playlist URLs or files",
                    icon = Icons.Outlined.PlaylistPlay,
                    onClick = { activeSubPage = "M3U8_MANAGER" }
                )
            }

            item {
                SecretSettingRow(
                    title = "Homai AI Assistant",
                    subtitle = "Chat with AI for recommendations & support",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { viewModel.openHomaiChat() }
                )
            }

            item {
                SecretSettingRow(
                    title = "Help Centre",
                    icon = Icons.Outlined.HelpOutline,
                    onClick = { showHelpCenterSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = com.example.ui.theme.AppTranslation.getString("language", selectedAudioIndex),
                    subtitle = audioOptions.getOrNull(selectedAudioIndex) ?: "Default System",
                    icon = Icons.Outlined.Language,
                    onClick = { showLanguageSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = com.example.ui.theme.AppTranslation.getString("app_update", selectedAudioIndex),
                    icon = Icons.Outlined.Refresh,
                    onClick = { showUpdateSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Clear Watch History",
                    subtitle = "Clear continue watching and viewed history",
                    icon = Icons.Outlined.Delete,
                    onClick = {
                        viewModel.clearMediaWatchHistory()
                        viewModel.clearWatchHistory()
                        Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                SecretSettingRow(
                    title = "Multiple Floating Player Mode",
                    subtitle = "Active limit: $maxFloatingPlayers Floating Players (Select 2-6 max)",
                    icon = Icons.Default.PictureInPictureAlt,
                    onClick = { showFloatingPlayerLimitSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Copyright Alert",
                    subtitle = "Copyright Disclaimer & DMCA Takedown Notice",
                    icon = Icons.Outlined.Shield,
                    onClick = { showCopyrightSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Privacy Policy & Terms of Service",
                    subtitle = "Usage Policy, Safety Guidelines & Data Security",
                    icon = Icons.Outlined.Shield,
                    onClick = { showPrivacyTermsSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = com.example.ui.theme.AppTranslation.getString("settings", selectedAudioIndex),
                    icon = Icons.Outlined.Settings,
                    onClick = { showAdvancedSettingsSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "About Us",
                    subtitle = "App overview, key features & user guide",
                    icon = Icons.Outlined.Info,
                    onClick = { showAboutUsSheet = true }
                )
            }

            item {
                SecretSettingRow(
                    title = "Support",
                    subtitle = "Community, Telegram, Facebook, Email & Messenger",
                    icon = Icons.Outlined.SupportAgent,
                    onClick = { showSupportSheet = true }
                )
            }

            // Footer Branding
            item {
                Spacer(modifier = Modifier.height(12.dp))
                DeveloperNoteFooter()
            }
        }
    } }

    // === INTERACTIVE BOTTOM SHEETS & DIALOGS FOR SECRET POT SETTINGS ===

    // 1. Profile Bottom Sheet
    if (showProfileSheet) {
        UserProfileBottomSheet(
            profile = profile,
            selectedAudioIndex = selectedAudioIndex,
            onDismiss = { showProfileSheet = false },
            onShowWatchHistory = {
                showProfileSheet = false
                showWatchHistorySheet = true
            },
            onShowWebVersion = {
                showProfileSheet = false
                showWebVersionView = true
            },
            onSwitchAccount = {
                showProfileSheet = false
                showSignInSheetInSettings = true
            },
            onSignOut = {
                viewModel.logout()
                showProfileSheet = false
            },
            onDeleteAccount = {
                showProfileSheet = false
                showDeleteAccountDialog = true
            },
            onShowCopyrightAlert = {
                showProfileSheet = false
                showCopyrightSheet = true
            },
            onShowFloatingPlayerLimit = {
                showProfileSheet = false
                showFloatingPlayerLimitSheet = true
            },
            onShowMasterAnime = {
                showProfileSheet = false
                showMasterAnimeBrowser = true
            },
            onShowCineStream = {
                showProfileSheet = false
                showCineStreamBrowser = true
            }
        )
    }

    if (showMasterAnimeBrowser) {
        MasterAnimeBrowserModal(
            url = "https://media.hmair.xyz",
            onDismiss = { showMasterAnimeBrowser = false }
        )
    }

    if (showCineStreamBrowser) {
        MasterAnimeBrowserModal(
            url = "https://cine.hmair.xyz/",
            onDismiss = { showCineStreamBrowser = false }
        )
    }

    if (showCopyrightSheet) {
        CopyrightBottomSheet(
            onDismiss = { showCopyrightSheet = false }
        )
    }

    if (showFloatingPlayerLimitSheet) {
        FloatingPlayerLimitBottomSheet(
            currentLimit = maxFloatingPlayers,
            onSelectLimit = { limit ->
                viewModel.setMaxFloatingPlayers(limit)
                Toast.makeText(context, "Floating player limit set to $limit", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showFloatingPlayerLimitSheet = false }
        )
    }

    if (showWatchHistorySheet) {
        WatchHistorySheet(
            viewModel = viewModel,
            onNavigateToPlayer = onNavigateToPlayer,
            onDismiss = { showWatchHistorySheet = false }
        )
    }

    // 2. Subscription Bottom Sheet
    if (showSubscriptionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSubscriptionSheet = false },
            containerColor = DeepSlate
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = "Subscription",
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Home Air TV VIP Subscription",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (effectiveIsPremium) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CyberGray,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Active Plan", color = TextSecondary, fontSize = 13.sp)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFF6B00))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(if (isRedeemActive) redeemPlanName.uppercase() else "VIP PREMIUM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("All Live Channels, 4K Movies & VidSrc Player Active", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            val planExpiryText = when {
                                isRedeemActive -> "Promo Pass Activated"
                                isLifetime -> "Renewal: Lifetime Pass Active"
                                SubscriptionManager.expiryDate.value != null -> "Renewal: Valid until ${SubscriptionManager.expiryDate.value}"
                                else -> "VIP Plan Active"
                            }
                            Text(planExpiryText, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CyberGray,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Current Plan", color = TextSecondary, fontSize = 13.sp)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Gray)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("FREE TIER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Limited Access to Live TV & Movies", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Upgrade to VIP for full experience", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        showSubscriptionPlanModalInProfile = true
                        showSubscriptionSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(if (effectiveIsPremium) "Manage Plan" else "Upgrade to VIP", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    com.example.ui.components.SubscriptionPlanModal(
        isVisible = showSubscriptionPlanModalInProfile,
        onDismiss = { showSubscriptionPlanModalInProfile = false }
    )

    if (showAiringReelsScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAiringReelsScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AiringFeedScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = {
                        showAiringReelsScreen = false
                        onNavigateToPlayer()
                    },
                    isHeaderVisible = false,
                    modifier = Modifier.fillMaxSize(),
                    onBackPress = { showAiringReelsScreen = false }
                )
            }
        }
    }

    if (showDiscoverFeedsScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDiscoverFeedsScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            DiscoverFeedsScreen(
                viewModel = viewModel,
                onNavigateToPlayer = {
                    showDiscoverFeedsScreen = false
                    onNavigateToPlayer()
                },
                onBackPress = { showDiscoverFeedsScreen = false }
            )
        }
    }

    // 3. Help Centre Bottom Sheet
    if (showHelpCenterSheet) {
        val langFontFamily = remember(selectedAudioIndex) { com.example.ui.theme.AppTranslation.getFontFamily(selectedAudioIndex) }
        ModalBottomSheet(
            onDismissRequest = { showHelpCenterSheet = false },
            containerColor = DeepSlate
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Outlined.HelpOutline,
                        contentDescription = "Help",
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = com.example.ui.theme.AppTranslation.getString("help_centre_title", selectedAudioIndex),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = langFontFamily,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User Manual Card with Step-by-Step Server Guidelines
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyberGray,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Guide", tint = Color(0xFFFF6B00), modifier = Modifier.size(20.dp))
                                Text(
                                    text = com.example.ui.theme.AppTranslation.getString("server_guide_title", selectedAudioIndex),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = langFontFamily,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        val steps = listOf(
                            "step_1_title" to "step_1_desc",
                            "step_2_title" to "step_2_desc",
                            "step_3_title" to "step_3_desc",
                            "step_4_title" to "step_4_desc",
                            "step_5_title" to "step_5_desc"
                        )

                        items(steps.size) { index ->
                            val (titleKey, descKey) = steps[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DeepSlate, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = com.example.ui.theme.AppTranslation.getString(titleKey, selectedAudioIndex),
                                    color = Color(0xFFFF9E40),
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = langFontFamily,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = com.example.ui.theme.AppTranslation.getString(descKey, selectedAudioIndex),
                                    color = TextSecondary,
                                    fontFamily = langFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:xubilas.era@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Home Air TV Support Inquiry")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Contact: xubilas.era@gmail.com", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF2C2C2E),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = "Email", tint = Color(0xFFFF6B00))
                        Text(
                            text = com.example.ui.theme.AppTranslation.getString("email_support", selectedAudioIndex),
                            color = Color.White,
                            fontFamily = langFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 4. Language Bottom Sheet
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = DeepSlate
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = com.example.ui.theme.AppTranslation.getString("select_language_title", selectedAudioIndex),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                audioOptions.forEachIndexed { idx, language ->
                    Surface(
                        onClick = {
                            viewModel.setAudioIndex(idx)
                            com.example.ui.theme.AppTranslation.applyAppLocale(context, idx)
                            Toast.makeText(context, "Language set to $language", Toast.LENGTH_SHORT).show()
                            showLanguageSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (idx == selectedAudioIndex) Color(0xFFFF6B00).copy(alpha = 0.2f) else CyberGray,
                        border = BorderStroke(1.dp, if (idx == selectedAudioIndex) Color(0xFFFF6B00) else BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(language, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (idx == selectedAudioIndex) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFFFF6B00))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // 5. App Update Bottom Sheet
    if (showUpdateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showUpdateSheet = false },
            containerColor = DeepSlate
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Update", tint = Color(0xFFFF6B00), modifier = Modifier.size(26.dp))
                    Text("Check for App Updates", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Installed Version: v$currentVersionName (Build $currentVersionCode)", color = TextSecondary, fontSize = 13.sp)

                Spacer(modifier = Modifier.height(16.dp))

                val statusText = when {
                    isCheckingUpdates -> "Connecting to AppsHub Update Server..."
                    updateCheckResult == "up_to_date" -> "You are using the latest version of Home Air TV."
                    updateCheckResult == "error" -> "Failed to fetch updates. Please check your network connection."
                    else -> "Click check to scan for recent stream engine and UI improvements."
                }

                Text(statusText, color = TextPrimary, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        isCheckingUpdates = true
                        scope.launch {
                            val result = com.example.update.AppUpdateManager.checkForUpdate(context)
                            isCheckingUpdates = false
                            when (result) {
                                is com.example.update.UpdateCheckResult.UpdateAvailable -> {
                                    viewModel.checkForAppUpdates(context)
                                    showUpdateSheet = false
                                }
                                is com.example.update.UpdateCheckResult.UpToDate -> {
                                    updateCheckResult = "up_to_date"
                                }
                                is com.example.update.UpdateCheckResult.Error -> {
                                    updateCheckResult = "error"
                                }
                            }
                        }
                    },
                    enabled = !isCheckingUpdates,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // 6. Advanced Settings Sheet
    if (showAdvancedSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAdvancedSettingsSheet = false },
            containerColor = DeepSlate
        ) {
            LazyColumn(
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text("Advanced Playback & System Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                item {
                    SettingsDropdownRow(
                        title = "ExoPlayer Stream Buffer",
                        subtitle = "Network pre-fetching & anti-freeze cushion",
                        icon = Icons.Default.Speed,
                        iconTint = Color(0xFFFF6B00),
                        options = bufferOptions,
                        selectedIndex = selectedBufferIndex,
                        onOptionSelected = { viewModel.setBufferIndex(it) }
                    )
                }

                item {
                    SettingsDropdownRow(
                        title = "Decoder Engine",
                        subtitle = "Video decoding acceleration & fallback",
                        icon = Icons.Default.Tune,
                        iconTint = Color(0xFFFF6B00),
                        options = decoderOptions,
                        selectedIndex = selectedDecoderIndex,
                        onOptionSelected = { viewModel.setDecoderIndex(it) }
                    )
                }

                item {
                    SettingsDropdownRow(
                        title = "Default Video Resolution",
                        subtitle = "Maximum playback quality",
                        icon = Icons.Default.HighQuality,
                        iconTint = Color(0xFFFF6B00),
                        options = qualityOptions,
                        selectedIndex = selectedQualityIndex,
                        onOptionSelected = { viewModel.setQualityIndex(it) }
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CyberGray,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hardware Acceleration", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("GPU decoding for smooth playback", color = TextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isHardwareAccel,
                                onCheckedChange = { viewModel.setHardwareAccel(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF6B00))
                            )
                        }
                    }
                }

                item {
                    val isBatterySaver by viewModel.batterySaverMode.collectAsState()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CyberGray,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery Saver Mode", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Enforces data saver 480p and 24 FPS to extend viewing time in low-power scenarios.", color = TextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isBatterySaver,
                                onCheckedChange = { viewModel.setBatterySaverMode(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF6B00))
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CyberGray,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Picture-in-Picture (PiP)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Keep playing when minimizing app", color = TextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isAutoPip,
                                onCheckedChange = { viewModel.setAutoPip(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF6B00))
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            LocalNotificationManager.showNotification(
                                context = context,
                                title = "Home Air TV Test Notification",
                                message = "Settings updated successfully!"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Test Notification Alert", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(text = "Delete Account?", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete your profile and clear all local session data?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount {
                            Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                ) {
                    Text("Confirm Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = false }
                ) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = DeepSlate
        )
    }

    if (showArchiveChannelsSheet) {
        val prefs by viewModel.channelPreferences.collectAsState()
        val archivedList = remember(prefs) { prefs.filter { it.isHidden } }
        
        AlertDialog(
            onDismissRequest = { showArchiveChannelsSheet = false },
            title = { Text("Archived Channels", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                if (archivedList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No archived channels found", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(archivedList) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.customGroup ?: "General", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.toggleHideChannel(
                                                IptvChannel(name = item.name, url = item.url, logo = "", group = item.customGroup ?: "General"),
                                                hide = false
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                    ) {
                                        Text("Activate", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showArchiveChannelsSheet = false }) {
                    Text("Close", color = NeonCyan)
                }
            },
            containerColor = Color(0xFF1C1C1E)
        )
    }

    if (showM3UManagerSheet) {
        val customPlaylistsState by viewModel.customPlaylists.collectAsState()
        
        AlertDialog(
            onDismissRequest = { showM3UManagerSheet = false },
            containerColor = if (isDark) DeepSlate else Color.White,
            title = { Text("M3U8 Playlists Manager", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Part 1: Add Playlist from URL
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDark) CyberGray else Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFFF6B00).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Import from URL", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            OutlinedTextField(
                                value = m3uFileNameInput,
                                onValueChange = { m3uFileNameInput = it },
                                label = { Text("Playlist Name", color = TextSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color(0xFFFF6B00),
                                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E),
                                    cursorColor = Color(0xFFFF6B00),
                                    focusedLabelColor = Color(0xFFFF6B00)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = m3uUrlInput,
                                onValueChange = { m3uUrlInput = it },
                                label = { Text("M3U / M3U8 Link URL", color = TextSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color(0xFFFF6B00),
                                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E),
                                    cursorColor = Color(0xFFFF6B00),
                                    focusedLabelColor = Color(0xFFFF6B00)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Button(
                                onClick = {
                                    if (m3uUrlInput.isBlank()) {
                                        Toast.makeText(context, "Please enter M3U URL", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val pName = m3uFileNameInput.ifBlank { "Custom Web Playlist" }
                                        viewModel.addCustomPlaylistFromUrl(pName, m3uUrlInput) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            if (success) {
                                                m3uFileNameInput = ""
                                                m3uUrlInput = ""
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Import Playlist Link", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    // Part 2: Upload M3U File
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDark) CyberGray else Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFFF6B00).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Upload Local M3U File", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            Button(
                                onClick = {
                                    m3uFilePickerLauncher.launch("*/*")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select .m3u / .m3u8 File", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    // Part 3: List of currently added Playlists
                    Text("Your Local Playlists", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (customPlaylistsState.isEmpty()) {
                        Text("No custom playlists added yet", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customPlaylistsState.forEach { pl ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color.White),
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFFF6B00).copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(pl.name, color = if (isDark) Color.White else Color(0xFFFF6B00), fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (pl.source == "url") pl.pathOrUrl else "Local File Upload",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteCustomPlaylist(pl.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showM3UManagerSheet = false }) {
                    Text("Done", color = if (isDark) NeonCyan else Color(0xFFFF6B00), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (selectedLocalVideoFile != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            OfflineVideoPlayerScreen(
                videoFile = selectedLocalVideoFile!!,
                playlist = selectedQueueVideos,
                onBack = {
                    try {
                        selectedLocalVideoFile?.delete()
                    } catch (_: Exception) {}
                    selectedLocalVideoFile = null
                    selectedQueueVideos.clear()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showSignInSheetInSettings) {
        SignInBottomSheet(
            viewModel = viewModel,
            onDismiss = { showSignInSheetInSettings = false }
        )
    }

    if (showPrivacyTermsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPrivacyTermsSheet = false },
            containerColor = DeepSlate
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "Privacy Policy & Terms of Service",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                item {
                    PrivacyPolicySectionCard(
                        title = "Announcement: Home Air TV - Terms & Conditions",
                        icon = Icons.Default.Announcement,
                        text = "Our priority is to build a transparent, user-friendly, and open platform for everyone. Here is what you need to know about using Home Air TV:\n\n" +
                               "1. Free & Open Access: Home Air TV is an open-source browsing application designed to help users discover and stream Live TV, Movies, Anime, and TV Shows without any paywalls.\n\n" +
                               "2. Content Aggregation: Home Air TV operates purely as an index and media browser. We do not host, store, or upload any video media on our servers. All contents are fetched from publicly available internet sources.\n\n" +
                               "3. No Commercial Subscriptions: We do not charge any fees, nor do we require credit card details. The service is 100% free for open-source community use.\n\n" +
                               "4. User Responsibility: Users are requested to use the app in compliance with local digital laws and guidelines.\n\n" +
                               "Explore smoothly and stay tuned for more updates! 🚀"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    PrivacyPolicySectionCard(
                        title = "Your Data Security & Privacy Policy",
                        icon = Icons.Default.Security,
                        text = "We value your digital privacy above everything else. Here is our complete commitment to handling user data:\n\n" +
                               " Zero Personal Data Collection: Home Air TV does not require account creation, mobile numbers, or emails to access basic browsing features.\n\n" +
                               " No Tracking or Logs: We do not track your watch history, search queries, or real-time location.\n\n" +
                               " Ad-Free Architecture: Unlike other streaming apps, Home Air TV runs completely clean without intrusive popup ads, trackers, or malicious third-party ad networks.\n\n" +
                               " Permissions: The app only requests basic network permissions required to stream video content to your device screen.\n\n" +
                               "Your security is guaranteed when you download from our official sources! "
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    PrivacyPolicySectionCard(
                        title = "Important Notice: Stay Safe & Avoid Fake APKs!",
                        icon = Icons.Default.Warning,
                        text = "To ensure 100% security for your Android devices while using Home Air TV, please read the following safety guidelines:\n\n" +
                               "1. Download Only from Official Sources: Always download or update the APK from our official site (xubilasappshub.xubilaswebdevcorp.shop) or our official Telegram channel https://t.me/HomeAirTv .\n" +
                               "Or Visit our official website homeairtv.xubilaswebdevcorp.shop.\n\n" +
                               "2. Safe Installation: Since this is an open-source APK project built directly by Xubilas Web Dev Corp, your Android system might display a standard side-loading prompt. Rest assured, our files are completely clean and free of malware or spy tools.\n\n" +
                               "3. Beware of Imitators: Do not download modified or untrusted APK files from random third-party blogs or forums.\n\n" +
                               "Enjoy your favorite shows with complete peace of mind! 🍿"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    PrivacyPolicySectionCard(
                        title = "Open Source Transparency: Why We Built Home Air TV",
                        icon = Icons.Default.Info,
                        text = "Many of you asked about the team and philosophy behind Home Air TV. Here is our vision:\n\n" +
                               " Developed by Xubilas Web Dev Corp: Home Air TV is an open-source initiative engineered to provide a lightweight, high-speed alternative to legacy platforms (like early-stage MovieBox).\n\n" +
                               " Community-Driven: Built for the community with a motto of transparency—no hidden subscriptions, no ad trackers, and no deceptive features.\n\n" +
                               " Developer Verification: You can inspect our agency background directly in the app's Profile section under Developer Credits, or visit xubilaswebdevcorp.shop anytime.\n\n" +
                               "Thank you for being part of our journey! Share your thoughts and suggestions below."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Text(
                        text = "Official Web Links:",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                item {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://homeairtv.xubilaswebdevcorp.shop/privacy"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Open Privacy Policy Link", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://homeairtv.xubilaswebdevcorp.shop/terms"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = BorderStroke(1.dp, Color(0xFFFF6B00)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFFFF6B00), modifier = Modifier.size(18.dp))
                            Text("Open Terms of Service Link", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showSupportSheet) {
        SupportBottomSheet(
            onDismiss = { showSupportSheet = false }
        )
    }

    if (showAboutUsSheet) {
        AboutUsBottomSheet(
            onDismiss = { showAboutUsSheet = false }
        )
    }

        activeWebPlayer?.let { state ->
            val imdbId = state.item.imdbId ?: "tt0111161"
            com.example.ui.components.CinemetaWebViewPlayer(
                imdbId = imdbId,
                title = state.item.title,
                type = state.item.type,
                season = state.season,
                episode = state.episode,
                allMediaItems = realMediaList,
                onSelectMedia = { selectedItem ->
                    viewModel.playMediaItem(selectedItem, 1, 1)

                },
                onClosePlayer = { activeWebPlayer = null },
                onFullScreenChange = { isFull ->
                    viewModel.setFullScreen(isFull)
                },
                modifier = Modifier.fillMaxSize(),
                isInPipMode = isInPipMode,
                onPlayingStateChanged = { playing ->
                    viewModel.setMediaPlaying(playing)
                },
                viewModel = viewModel,
                mediaItem = state.item
            )
        }
    }

    if (showWebVersionView) {
        WebBrowserDialog(
            url = "https://homeairtv.xubilaswebdevcorp.shop/",
            onDismiss = { showWebVersionView = false }
        )
    }

    if (showBottomNavCustomizerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomNavCustomizerSheet = false },
            containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF161618) else Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = "Customize Tabs",
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Customize Navigation",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color.White else Color(0xFF1C1C1E)
                        )
                    }
                    IconButton(
                        onClick = { showBottomNavCustomizerSheet = false }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDark) Color.LightGray else Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Customize bottom navigation slots. You can assign any slot to your favorite sections like Live TV, Dashboard, Sports, Anime, Airing Reels, Air Stream, or Downloads.",
                    fontSize = 12.sp,
                    color = if (isDark) Color.LightGray.copy(alpha = 0.8f) else Color.DarkGray.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                val slotOptions = listOf(
                    Triple("Browse", "Browse", Icons.Outlined.Dashboard),
                    Triple("Air", "Air Stream", Icons.Default.Tv),
                    Triple("Live", "Live TV", Icons.Default.LiveTv),
                    Triple("Feeds", "Feeds", Icons.Default.DynamicFeed),
                    Triple("Sports", "Sports", Icons.Default.SportsSoccer),
                    Triple("Master Anime", "Anime Hub", Icons.Default.AutoAwesome),
                    Triple("Airing", "Airing Reels", Icons.Default.Movie),
                    Triple("Downloads", "Downloads", Icons.Outlined.Download)
                )

                // SLOT 1: Browse Slot (Tab 2)
                Text(
                    text = "Slot 2 (Default: Browse)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF1C1C1E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(slotOptions) { (key, label, icon) ->
                        val isSelected = browseSlotType == key
                        Surface(
                            onClick = { viewModel.updateBrowseSlotType(key) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFFF6B00).copy(alpha = 0.15f) else (if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7)),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFF6B00) else Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color(0xFFFF6B00) else (if (isDark) Color.Gray else Color.DarkGray),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFFFF6B00) else (if (isDark) Color.White else Color(0xFF1C1C1E))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // SLOT 2: Air Slot (Tab 3)
                Text(
                    text = "Slot 3 (Default: Air Stream)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF1C1C1E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(slotOptions) { (key, label, icon) ->
                        val isSelected = airSlotType == key
                        Surface(
                            onClick = { viewModel.updateAirSlotType(key) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFFF6B00).copy(alpha = 0.15f) else (if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7)),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFF6B00) else Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color(0xFFFF6B00) else (if (isDark) Color.Gray else Color.DarkGray),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFFFF6B00) else (if (isDark) Color.White else Color(0xFF1C1C1E))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // SLOT 3: Download Slot (Tab 4)
                Text(
                    text = "Slot 4 (Default: Downloads)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF1C1C1E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(slotOptions) { (key, label, icon) ->
                        val isSelected = downloadsSlotType == key
                        Surface(
                            onClick = { viewModel.updateDownloadsSlotType(key) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFFF6B00).copy(alpha = 0.15f) else (if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7)),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFF6B00) else Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color(0xFFFF6B00) else (if (isDark) Color.Gray else Color.DarkGray),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFFFF6B00) else (if (isDark) Color.White else Color(0xFF1C1C1E))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { showBottomNavCustomizerSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Save Configuration",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SecretSettingRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val borderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val iconBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFF4)
    val iconTint = if (isDark) Color.White else Color(0xFF1C1C1E)
    val titleColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circular Icon Container
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFFF6B00),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BufferOptionRow(
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) LightAccent.copy(alpha = 0.5f) else SpaceBlack)
            .border(width = 2.dp, color = if (isSelected) NeonCyan else Color.Transparent, shape = RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = TextSecondary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}


// ==========================================
// 7. UTILITY COMMON WIDGETS
// ==========================================
@Composable
fun EmptyStateView(
    title: String,
    tip: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = "Empty",
            tint = TextSecondary,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tip,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = NeonMagenta,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Failed to synchronize",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
        ) {
            Text("Retry Sync", color = Color.Black)
        }
    }
}

// Utility function to draw clean border
fun BoxBorder(color: Color) = BorderStroke(1.dp, color)

@Composable
fun ProfileHeaderButton(
    userProfile: com.example.ui.viewmodel.UserProfile?,
    onNavigateToSettings: () -> Unit,
    onOpenSignIn: () -> Unit
) {
    if (userProfile != null) {
        // Circular Avatar Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(DeepSlate)
                .border(1.5.dp, NeonCyan, CircleShape)
                .clickable { onNavigateToSettings() }
                .testTag("header_profile_avatar"),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(userProfile.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "User Profile",
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // Glowing Sign In Button
        Button(
            onClick = onOpenSignIn,
            colors = ButtonDefaults.buttonColors(containerColor = DeepSlate),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NeonMagenta),
            modifier = Modifier
                .height(34.dp)
                .testTag("header_sign_in_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Sign In",
                    tint = NeonMagenta,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Sign In",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInBottomSheet(
    viewModel: StreamViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pendingDeviceAccounts by remember { mutableStateOf<List<String>?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpaceBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Logo & Title
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeepSlate)
                    .border(
                        1.5.dp,
                        Brush.linearGradient(listOf(NeonCyan, NeonPurple)),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Logo",
                    tint = NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "IPTV STREAMING PORTAL",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                ),
                color = TextPrimary
            )

            Text(
                text = "Access live streams, administrative logs, and super admin controls",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // GOOGLE SIGN IN EXCLUSIVE CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.2.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GOOGLE AUTHENTICATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF4285F4)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Fast & secure single sign-on with your Google account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val context = LocalContext.current


                    // Google Button
                    Button(
                        onClick = {
                            viewModel.signInWithGoogle(
                                context = context,
                                onSuccess = {
                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_auth_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OfficialGoogleLogo(modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        pendingDeviceAccounts?.let { accounts ->
            RealGoogleAccountsDialog(
                accounts = accounts,
                onDismiss = { pendingDeviceAccounts = null },
                onAccountSelected = { selectedEmail ->
                    viewModel.login(selectedEmail, selectedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }, "")
                    pendingDeviceAccounts = null
                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
        }
    }
}

data class WebPlayerState(
    val item: MediaItem,
    val season: Int = 1,
    val episode: Int = 1
)

// ==========================================
// 8. MEDIA HUB SCREEN (MOVIES & ANIME)
// ==========================================
@Composable
fun MediaHubScreen(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAirTab: () -> Unit = {},
    isHeaderVisible: Boolean = true
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val latestReleases by viewModel.latestReleases.collectAsState()
    val filteredItems by viewModel.filteredMediaItems.collectAsState()
    val selectedCategory by viewModel.selectedMediaCategory.collectAsState()
    val searchQuery by viewModel.mediaSearchQuery.collectAsState()
    val isSearchingMedia by viewModel.isSearchingMedia.collectAsState()
    val selectedAudioIndex by viewModel.audioIndex.collectAsState()
    val mediaWatchHistory by viewModel.mediaWatchHistory.collectAsState()
    val mediaFavorites by viewModel.mediaFavorites.collectAsState()
    val isInPipMode by viewModel.isInPipMode.collectAsState()

    var selectedItemForDetail by remember { mutableStateOf<MediaItem?>(null) }
    var autoPlayTrailerOnOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.tabReselectEvent.collect { tabIndex ->
            if (tabIndex == 1) {
                selectedItemForDetail = null
            }
        }
    }
    var activeWebPlayer by remember { mutableStateOf<WebPlayerState?>(null) }
    val showCategoryGridDialog by viewModel.showCategoriesGrid.collectAsState()

    androidx.activity.compose.BackHandler(enabled = activeWebPlayer != null || selectedItemForDetail != null) {
        if (activeWebPlayer != null) {
            activeWebPlayer = null
        } else if (selectedItemForDetail != null) {
            selectedItemForDetail = null
        }
    }

    val categoryDetails = remember {
        listOf(
            Triple("Live TV", Icons.Default.LiveTv, Color(0xFFEF4444)),
            Triple("All", Icons.Default.GridOn, Color(0xFF10B981)),
            Triple("Movies", Icons.Default.Theaters, Color(0xFF3B82F6)),
            Triple("Anime", Icons.Default.LiveTv, Color(0xFFEC4899)),
            Triple("Series & TV Shows", Icons.Default.Tv, Color(0xFF8B5CF6)),
            Triple("Anime Movies", Icons.Default.Movie, Color(0xFFF43F5E)),
            Triple("Bangla Cinema & Natok", Icons.Default.MovieFilter, Color(0xFF006A4E)),
            Triple("Anime Series", Icons.Default.LiveTv, Color(0xFFEC4899)),
            Triple("K-Dramas", Icons.Default.Favorite, Color(0xFF06B6D4)),
            Triple("Action", Icons.Default.Whatshot, Color(0xFFEF4444)),
            Triple("Sci-Fi", Icons.Default.Public, Color(0xFF00E5FF)),
            Triple("Hindi Cinema", Icons.Default.Movie, Color(0xFFFF9933)),
            Triple("Hindi Series", Icons.Default.Tv, Color(0xFFFF5722)),
            Triple("Hindi Dubbed", Icons.Default.RecordVoiceOver, Color(0xFFE91E63)),
            Triple("Hindi Dubbed K-Dramas", Icons.Default.FavoriteBorder, Color(0xFFE91E63))
        )
    }

    LaunchedEffect(Unit) {
        viewModel.tabReselectEvent.collect { tabIndex ->
            if (tabIndex == 0 || tabIndex == 1 || tabIndex == 3) {
                activeWebPlayer = null
            }
        }
    }

    val categories = remember {
        listOf(
            "Live TV",
            "Latest",
            "All",
            "Movies",
            "Anime",
            "Series & TV Shows",
            "Anime Movies",
            "Bangla Cinema & Natok",
            "Anime Series",
            "K-Dramas",
            "Action",
            "Sci-Fi",
            "Hindi Cinema",
            "Hindi Series",
            "Hindi Dubbed",
            "Hindi Dubbed K-Dramas"
        )
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) SpaceBlack else Color(0xFFF5F5F7)
    val textColor = if (isDark) TextPrimary else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) TextSecondary else Color(0xFF757575)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .statusBarsPadding()
        ) {
            var isSearchExpanded by remember { mutableStateOf(false) }

            val context = LocalContext.current


            val speechRecognizerLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val data = result.data
                    val matches = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    if (!matches.isNullOrEmpty()) {
                        viewModel.setMediaSearchQuery(matches[0])
                        isSearchExpanded = true
                    }
                }
            }

            var isListening by remember { mutableStateOf(false) }
            val micPulse by animateFloatAsState(
                targetValue = if (isListening) 1.2f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            // Top Header with Smooth Animated Search Expansion
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
                ) {
                    AnimatedContent(
                        targetState = isSearchExpanded || searchQuery.isNotEmpty(),
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + slideInHorizontally { width -> width })
                                .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally { width -> -width })
                        },
                        label = "HeaderSearchTransition"
                    ) { searchActive ->
                        if (searchActive) {
                            // Smooth Expanded Header Search Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(DeepSlate)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        isSearchExpanded = false
                                        viewModel.setMediaSearchQuery("")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Close Search",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setMediaSearchQuery(it) },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onSearch = {
                                            viewModel.triggerImmediateMediaSearch(searchQuery)
                                            keyboardController?.hide()
                                        }
                                    ),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary, fontSize = 15.sp),
                                    cursorBrush = SolidColor(NeonCyan),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search movies, anime, TV series...",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = TextSecondary,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                                )

                                if (isSearchingMedia) {
                                    CircularProgressIndicator(
                                        color = NeonCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(end = 4.dp)
                                    )
                                }

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setMediaSearchQuery("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // AI Search Button
                                    IconButton(
                                        onClick = {
                                            val query = searchQuery
                                            viewModel.openHomaiChat()
                                            viewModel.sendHomaiMessage("Find $query")
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "AI Search",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        isListening = true
                                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                                        }
                                        try {
                                            speechRecognizerLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            isListening = false
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).scale(if (isListening) micPulse else 1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice Search",
                                        tint = if (isListening) Color.Red else NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            // Standard Header Title View with Header Search Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFFF6B00), Color(0xFFFF3D00))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MovieFilter,
                                                contentDescription = "Media Hub",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = com.example.ui.theme.AppTranslation.getString("movies_anime_hub", selectedAudioIndex),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                letterSpacing = 0.2.sp
                                            ),
                                            color = textColor
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { isSearchExpanded = true },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(DeepSlate)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = NeonCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = showCategoryGridDialog,
                        onClick = { viewModel.setShowCategoriesGrid(true) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = "Explore",
                                    tint = if (showCategoryGridDialog) Color.Black else NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Categories",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = DeepSlate,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = showCategoryGridDialog,
                            borderColor = BorderColor,
                            selectedBorderColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                items(items = categories, key = { it }) { category ->
                    val isSelected = selectedCategory == category
                    val chipIcon = when (category) {
                        "All" -> Icons.Default.Apps
                        "Bangla Cinema & Natok" -> Icons.Default.MovieFilter
                        "Movies" -> Icons.Default.Movie
                        "Series & TV Shows" -> Icons.Default.Tv
                        "Anime" -> Icons.Default.Star
                        "Anime Series" -> Icons.Default.Tv
                        "Anime Movies" -> Icons.Default.Movie
                        "K-Dramas" -> Icons.Default.Favorite
                        "Action" -> Icons.Default.FlashOn
                        "Sci-Fi" -> Icons.Default.Star
                        "Hindi Cinema" -> Icons.Default.MovieFilter
                        "Hindi Series" -> Icons.Default.Tv
                        "Hindi Dubbed" -> Icons.Default.Mic
                        "Hindi Dubbed K-Dramas" -> Icons.Default.Favorite
                        else -> Icons.Default.Category
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (category == "Live TV") {
                                onNavigateToAirTab()
                            } else {
                                viewModel.setSelectedMediaCategory(category)
                            }
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = chipIcon,
                                    contentDescription = category,
                                    tint = if (isSelected) Color.Black else NeonCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = category,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = DeepSlate,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BorderColor,
                            selectedBorderColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Main Content Area
            var isRefreshing by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()
            @OptIn(ExperimentalMaterial3Api::class)
            val mediaPullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

            @OptIn(ExperimentalMaterial3Api::class)
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadMediaItems(forceRefresh = true)
                        kotlinx.coroutines.delay(600)
                        isRefreshing = false
                    }
                },
                state = mediaPullState,
                indicator = {
                    com.example.ui.components.WavePullToRefreshIndicator(
                        state = mediaPullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                when (val state = mediaState) {
                is UiState.Loading -> {
                    ShimmerPosterGrid(
                        columns = 2,
                        itemCount = 8,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is UiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.loadMediaItems(forceRefresh = true) }
                    )
                }

                is UiState.Success<*> -> {
                    val allItems = (state.data as? List<*>)?.filterIsInstance<MediaItem>() ?: emptyList()

                    val multiRowCategories = listOf("All", "Latest", "Movies", "Series & TV Shows", "Anime")
                    val isMultiRowTab = searchQuery.isBlank() && (selectedCategory in multiRowCategories)

                    if (!isMultiRowTab) {
                        // Display Grid view for specific search query or single genre chip selection
                        if (filteredItems.isEmpty()) {
                            if (isSearchingMedia) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = NeonCyan,
                                            modifier = Modifier.size(36.dp),
                                            strokeWidth = 3.dp
                                        )
                                        Text(
                                            text = "Searching for \"$searchQuery\"...",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Scanning TMDB, Anime & Cloud servers",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            } else {
                                EmptyStateView(
                                    title = "No Results Found",
                                    tip = "Try another search term or select 'All' to browse categories."
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Search Results (${filteredItems.size})" else "$selectedCategory (${filteredItems.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                val gridState = rememberLazyGridState()
                                LaunchedEffect(searchQuery, selectedCategory) {
                                    try {
                                        gridState.scrollToItem(0)
                                    } catch (e: Exception) {
                                        // Ignore scroll errors
                                    }
                                }
                                LaunchedEffect(gridState) {
                                    snapshotFlow {
                                        val layoutInfo = gridState.layoutInfo
                                        val totalItemsNumber = layoutInfo.totalItemsCount
                                        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        lastVisibleItemIndex >= totalItemsNumber - 4 && totalItemsNumber > 0
                                    }.collect { isNearEnd ->
                                        if (isNearEnd) {
                                            viewModel.loadMoreMedia()
                                        }
                                    }
                                }

                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(items = filteredItems, key = { item -> item.id }) { item ->
                                        MediaCard(
                                            item = item,
                                            onClick = { viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                            onPlayClick = {
                                                viewModel.playMediaItem(item, 1, 1)
                                                        onNavigateToPlayer()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Display Organized Row-by-Category Layouts
                        val banglaList = remember(allItems) {
                            allItems.filter { it.category.equals("Bangla Cinema & Natok", ignoreCase = true) }
                                .sortedWith(
                                    compareByDescending<MediaItem> { it.year.toIntOrNull() ?: 0 }
                                        .thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
                                )
                        }
                        val hindiMoviesList = remember(allItems) { allItems.filter { it.category.equals("Hindi Cinema", ignoreCase = true) } }
                        val hindiSeriesList = remember(allItems) { allItems.filter { it.category.equals("Hindi Series", ignoreCase = true) } }
                        val hindiDubbedList = remember(allItems) { allItems.filter { it.category.equals("Hindi Dubbed", ignoreCase = true) } }

                        val hindiDubbedKDramaList = remember(allItems) {
                            val excludeKeywords = listOf("quiet please", "anime", "animation", "manga", "hentai", "webtoon", "manhwa", "yaoi", "bl ", "boy's love", "cartoon")
                            allItems.filter { it.category.equals("Hindi Dubbed K-Dramas", ignoreCase = true) }.filter { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                excludeKeywords.none { text.contains(it) }
                            }
                        }
                        val moviesList = remember(allItems) { allItems.filter { it.category.equals("Movies", ignoreCase = true) } }

                        val animeSeriesList = remember(allItems) {
                            val items = allItems.filter { it.category.equals("Anime Series", ignoreCase = true) }
                            val matureKeywords = listOf(
                                "ecchi", "hentai", "erotica", "nude", "sex", "harem", "18+", "adult",
                                "lust", "seduct", "fetish", "interspecies", "dxd", "redo of healer",
                                "kiss x sis", "to love ru", "testament", "prison school"
                            )
                            val (mature, nonMature) = items.partition { item ->
                                val text = (item.title + " " + item.description + " " + item.category).lowercase()
                                matureKeywords.any { text.contains(it) }
                            }
                            val familyActionKeywords = listOf(
                                "spy", "family", "action", "comedy", "funny", "hero", "demon", "slayer",
                                "naruto", "piece", "dragon", "ghibli", "doraemon", "pokemon", "detective",
                                "titan", "hunter", "bleach", "kaisen", "jujutsu", "haikyu", "punch",
                                "ninja", "academy", "conan", "shin-chan", "cat", "friend", "school",
                                "game", "magic", "sports", "volleyball", "basketball", "thriller", "adventure"
                            )
                            val sortedNonMature = nonMature.sortedByDescending { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                var score = 0
                                familyActionKeywords.forEach { kw ->
                                    if (text.contains(kw)) score += 3
                                }
                                val ratingDouble = item.rating.toDoubleOrNull() ?: 0.0
                                score += (ratingDouble * 2).toInt()
                                score
                            }
                            sortedNonMature + mature
                        }

                        val animeMoviesList = remember(allItems) {
                            val items = allItems.filter { it.category.equals("Anime Movies", ignoreCase = true) }
                            val matureKeywords = listOf(
                                "ecchi", "hentai", "erotica", "nude", "sex", "harem", "18+", "adult",
                                "lust", "seduct", "fetish", "interspecies", "dxd", "redo of healer",
                                "kiss x sis", "to love ru", "testament", "prison school"
                            )
                            val (mature, nonMature) = items.partition { item ->
                                val text = (item.title + " " + item.description + " " + item.category).lowercase()
                                matureKeywords.any { text.contains(it) }
                            }
                            val familyActionKeywords = listOf(
                                "spy", "family", "action", "comedy", "funny", "hero", "demon", "slayer",
                                "naruto", "piece", "dragon", "ghibli", "doraemon", "pokemon", "detective",
                                "titan", "hunter", "bleach", "kaisen", "jujutsu", "haikyu", "punch",
                                "ninja", "academy", "conan", "shin-chan", "cat", "friend", "school",
                                "game", "magic", "sports", "volleyball", "basketball", "thriller", "adventure"
                            )
                            val sortedNonMature = nonMature.sortedByDescending { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                var score = 0
                                familyActionKeywords.forEach { kw ->
                                    if (text.contains(kw)) score += 3
                                }
                                val ratingDouble = item.rating.toDoubleOrNull() ?: 0.0
                                score += (ratingDouble * 2).toInt()
                                score
                            }
                            sortedNonMature + mature
                        }

                        val allAnimeItems = remember(allItems) {
                            allItems.filter { it.category.contains("Anime", ignoreCase = true) || it.type.equals("anime", ignoreCase = true) }
                        }

                        val latestAnimeList = remember(allItems, latestReleases, allAnimeItems) {
                            val fromLatestReleases = latestReleases.filter { it.category.contains("Anime", ignoreCase = true) }
                            val explicit = (fromLatestReleases + allAnimeItems).filter { 
                                (it.year.toIntOrNull() ?: 0) >= 2024 || it.category.contains("Latest", ignoreCase = true) 
                            }
                            val combined = (explicit + allAnimeItems).distinctBy { it.id }
                            combined.sortedWith(
                                compareByDescending<MediaItem> { it.year.toIntOrNull() ?: 0 }
                                    .thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
                            )
                        }

                        val hotAnimeList = remember(allAnimeItems) {
                            val hotKeywords = listOf(
                                "naruto", "demon slayer", "jujutsu", "attack on titan", "one piece", "bleach",
                                "solo leveling", "dragon ball", "hunter", "hero academia", "death note",
                                "chainsaw man", "kaiju", "spy x family", "dandadan", "wind breaker"
                            )
                            val matches = allAnimeItems.filter { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                hotKeywords.any { text.contains(it) } || (item.rating.toDoubleOrNull() ?: 0.0) >= 7.8
                            }
                            (matches + allAnimeItems).distinctBy { it.id }
                        }

                        val topAnimeList = remember(allAnimeItems) {
                            allAnimeItems.sortedWith(
                                compareByDescending<MediaItem> { it.rating.toDoubleOrNull() ?: 0.0 }
                                    .thenByDescending { it.year.toIntOrNull() ?: 0 }
                            )
                        }

                        val actionAnimeList = remember(allAnimeItems) {
                            val actionKeywords = listOf(
                                "action", "fight", "slayer", "titan", "hunter", "hero", "punch", "ninja", "dragon",
                                "jujutsu", "bleach", "piece", "adventure", "battle", "sword", "solo leveling",
                                "chainsaw", "black clover", "kaiju", "vinland", "naruto", "boruto"
                            )
                            val filtered = allAnimeItems.filter { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                actionKeywords.any { text.contains(it) }
                            }
                            filtered.ifEmpty { allAnimeItems }
                        }

                        val romanticAnimeList = remember(allAnimeItems) {
                            val romanceKeywords = listOf(
                                "romance", "romantic", "love", "lie", "kimi", "your name", "weathering", "horimiya",
                                "kaguya", "dress-up", "silent voice", "toradora", "fruits basket", "clannad", "heart",
                                "couple", "girlfriend", "darling", "yamada", "maid", "sweet", "ao haru", "rent-a-girlfriend",
                                "tonikawa", "kubo", "insomniacs", "tomo-chan", "higehiro", "skip and loafer", "dangers in my heart"
                            )
                            val filtered = allAnimeItems.filter { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                romanceKeywords.any { text.contains(it) }
                            }
                            if (filtered.isNotEmpty()) filtered else {
                                val fallback = allAnimeItems.filter { item ->
                                    val title = item.title.lowercase()
                                    title.contains("love") || title.contains("heart") || title.contains("girl") || title.contains("voice")
                                }
                                if (fallback.isNotEmpty()) fallback else allAnimeItems.take(15)
                            }
                        }

                        val comedyAnimeList = remember(allAnimeItems) {
                            val comedyKeywords = listOf(
                                "comedy", "funny", "spy", "gintama", "kono suba", "saiki", "bocchi", "nichijou",
                                "mashle", "shin-chan", "doraemon", "school", "devil", "humor", "parody", "grand blue",
                                "daily lives", "assassination classroom", "dr. stone", "great teacher"
                            )
                            val filtered = allAnimeItems.filter { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                comedyKeywords.any { text.contains(it) }
                            }
                            if (filtered.isNotEmpty()) filtered else {
                                val fallback = allAnimeItems.filter { item ->
                                    val text = (item.title + " " + item.description).lowercase()
                                    text.contains("spy") || text.contains("family") || text.contains("school") || text.contains("friend")
                                }
                                if (fallback.isNotEmpty()) fallback else allAnimeItems.take(15)
                            }
                        }

                        val seriesList = remember(allItems) { allItems.filter { it.category.equals("Series & TV Shows", ignoreCase = true) } }
                        val actionList = remember(allItems) { allItems.filter { it.category.equals("Action", ignoreCase = true) } }
                        val sciFiList = remember(allItems) { allItems.filter { it.category.equals("Sci-Fi", ignoreCase = true) } }
                        val kDramaList = remember(allItems) {
                            val excludeKeywords = listOf("quiet please", "anime", "animation", "manga", "hentai", "webtoon", "manhwa", "yaoi", "bl ", "boy's love", "cartoon")
                            allItems.filter { it.category.equals("K-Dramas", ignoreCase = true) }.filter { item ->
                                val text = (item.title + " " + item.description).lowercase()
                                excludeKeywords.none { text.contains(it) }
                            }
                        }

                        val popularList = remember(allItems) {
                            allItems.filter { (it.rating.toDoubleOrNull() ?: 0.0) >= 6.5 }.take(10).ifEmpty { allItems.take(7) }
                        }
                        val suggestedList = remember(allItems) {
                            allItems.filter { (it.rating.toDoubleOrNull() ?: 0.0) >= 6.5 }.take(8).ifEmpty { allItems.take(5) }
                        }

                        val columnState = rememberLazyListState()
                        LaunchedEffect(columnState) {
                            snapshotFlow {
                                val layoutInfo = columnState.layoutInfo
                                val totalItemsNumber = layoutInfo.totalItemsCount
                                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisibleItemIndex >= totalItemsNumber - 2 && totalItemsNumber > 0
                            }.collect { isNearEnd ->
                                if (isNearEnd) {
                                    viewModel.loadMoreMedia()
                                }
                            }
                        }

                        LazyColumn(
                            state = columnState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Render based on selected major tab
                            when (selectedCategory) {
                                "Movies" -> {
                                    if (popularList.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.example.ui.components.AutoScrollingBannerCarousel(
                                                items = popularList,
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); viewModel.playMediaItem(item, 1, 1); activeWebPlayer = WebPlayerState(item, 1, 1) }
                                            )
                                        }
                                    }
                                    if (moviesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83c\udfac English & Blockbuster Movies",
                                                items = moviesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Movies") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (hindiMoviesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Hindi Cinema",
                                                items = hindiMoviesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Cinema") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (hindiDubbedList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Hindi Dubbed Movies",
                                                items = hindiDubbedList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Dubbed") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (suggestedList.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            com.example.ui.components.SuggestedBannerCarousel(
                                                items = suggestedList,
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                    if (animeMoviesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Anime Feature Movies",
                                                items = animeMoviesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime Movies") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (sciFiList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83d\ude80 Sci-Fi & Fantasy Movies",
                                                items = sciFiList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Sci-Fi") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (actionList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Action Movies",
                                                items = actionList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Action") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                }

                                "Series & TV Shows" -> {
                                    if (popularList.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.example.ui.components.AutoScrollingBannerCarousel(
                                                items = popularList,
                                                onItemClick = { item ->
                                                    viewModel.preScrapeMediaItem(item)
                                                    viewModel.playMediaItem(item, 1, 1)
                                                    activeWebPlayer = WebPlayerState(item, 1, 1)
                                                }
                                            )
                                        }
                                    }
                                    if (seriesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Top Series & TV Shows",
                                                items = seriesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Series & TV Shows") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (hindiSeriesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83c\udfac Hindi Web Series",
                                                items = hindiSeriesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Series") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (hindiDubbedKDramaList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Hindi Dubbed K-Dramas",
                                                items = hindiDubbedKDramaList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Dubbed K-Dramas") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (suggestedList.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            com.example.ui.components.SuggestedBannerCarousel(
                                                items = suggestedList,
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                    if (kDramaList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " K-Dramas & Asian TV",
                                                items = kDramaList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("K-Dramas") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                }

                                "Anime" -> {
                                    val animeFeatured = (hotAnimeList + animeSeriesList + animeMoviesList).take(6)
                                    if (animeFeatured.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.example.ui.components.AutoScrollingBannerCarousel(
                                                items = animeFeatured,
                                                onItemClick = { item ->
                                                    viewModel.preScrapeMediaItem(item)
                                                    viewModel.playMediaItem(item, 1, 1)
                                                    activeWebPlayer = WebPlayerState(item, 1, 1)
                                                }
                                            )
                                        }
                                    }

                                    // 1. Hot Anime Category Row
                                    if (hotAnimeList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "🔥 Hot & Trending Anime",
                                                items = hotAnimeList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    // 2. Latest Anime Category Row
                                    if (latestAnimeList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "✨ Latest Airing Anime",
                                                items = latestAnimeList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    // 3. Top Anime Category Row
                                    if (topAnimeList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "⭐ Top Rated Anime",
                                                items = topAnimeList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    // 4. Action Anime Category Row
                                    if (actionAnimeList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "⚔️ Action Anime",
                                                items = actionAnimeList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (suggestedList.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            com.example.ui.components.SuggestedBannerCarousel(
                                                items = suggestedList,
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }

                                    // 5. Romantic Anime Category Row
                                    if (romanticAnimeList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "💖 Romantic Anime",
                                                items = romanticAnimeList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    // 6. Comedy Anime Category Row
                                    if (comedyAnimeList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "😂 Comedy Anime",
                                                items = comedyAnimeList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    // 7. Anime Series Category Row
                                    if (animeSeriesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "📺 Anime Series",
                                                items = animeSeriesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime Series") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    // 8. Anime Feature Movies Category Row
                                    if (animeMoviesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "🎬 Anime Feature Movies",
                                                items = animeMoviesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime Movies") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                }

                                "Latest" -> {
                                    val latestList = if (latestReleases.isNotEmpty()) latestReleases else allItems.take(25)
                                    val latestMovies = latestList.filter { it.category.contains("Movie", ignoreCase = true) || it.type.equals("movie", ignoreCase = true) }
                                    val latestSeries = latestList.filter { it.category.contains("Series", ignoreCase = true) || it.type.equals("series", ignoreCase = true) || it.type.equals("tv", ignoreCase = true) }
                                    val latestAnime = latestList.filter { it.category.contains("Anime", ignoreCase = true) }

                                    if (latestList.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.example.ui.components.AutoScrollingBannerCarousel(
                                                items = latestList.take(6),
                                                onItemClick = { item ->
                                                    viewModel.preScrapeMediaItem(item)
                                                    viewModel.playMediaItem(item, 1, 1)
                                                    activeWebPlayer = WebPlayerState(item, 1, 1)
                                                }
                                            )
                                        }
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\u2728 All Fresh & Latest",
                                                items = latestList,
                                                onSeeAllClick = { },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (latestMovies.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83c\udfac Latest Release Movies",
                                                items = latestMovies,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Movies") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (latestSeries.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Latest Airing Series & TV Shows",
                                                items = latestSeries,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Series & TV Shows") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                    if (latestAnime.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Latest Airing Anime",
                                                items = latestAnime,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }
                                }

                                else -> { // "All"
                                    // Featured Banner moved above all rows, left to right edge-to-edge
                                    if (popularList.isNotEmpty()) {
                                        item {
                                            com.example.ui.components.AutoScrollingBannerCarousel(
                                                items = popularList,
                                                onItemClick = { item ->
                                                    viewModel.preScrapeMediaItem(item)
                                                    viewModel.playMediaItem(item, 1, 1)
                                                    activeWebPlayer = WebPlayerState(item, 1, 1)
                                                },
                                                edgeToEdge = true
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }

                                    if (mediaWatchHistory.isNotEmpty()) {
                                        item {
                                            ContinueWatchingRowSection(
                                                title = "Continue Watching",
                                                items = mediaWatchHistory.take(15),
                                                viewModel = viewModel,
                                                onSeeAllClick = { },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item }
                                            )
                                        }
                                    }

                                    val displayLatest = if (latestReleases.isNotEmpty()) latestReleases else allItems.take(15)
                                    if (displayLatest.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\u2728 Latest",
                                                items = displayLatest,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Latest") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (mediaFavorites.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " My Favorites",
                                                items = mediaFavorites,
                                                onSeeAllClick = { },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item }
                                            )
                                        }
                                    }

                                    if (moviesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83d\udd25 Popular Movies",
                                                items = moviesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Movies") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (banglaList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Bangla Cinema & Natok",
                                                items = banglaList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Bangla Cinema & Natok") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (seriesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Top Series & TV Shows",
                                                items = seriesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Series & TV Shows") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (animeSeriesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Anime Series",
                                                items = animeSeriesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime Series") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (animeMoviesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83c\udfac Anime Feature Movies",
                                                items = animeMoviesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Anime Movies") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (kDramaList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " K-Dramas & Asian Cinema",
                                                items = kDramaList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("K-Dramas") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (actionList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Action & Blockbusters",
                                                items = actionList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Action") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (sciFiList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83d\ude80 Sci-Fi & Fantasy",
                                                items = sciFiList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Sci-Fi") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (suggestedList.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            com.example.ui.components.SuggestedBannerCarousel(
                                                items = suggestedList,
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }

                                    if (hindiMoviesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Hindi Cinema",
                                                items = hindiMoviesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Cinema") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (hindiSeriesList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = "\ud83c\udfac Hindi Web Series",
                                                items = hindiSeriesList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Series") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (hindiDubbedList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Hindi Dubbed Movies",
                                                items = hindiDubbedList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Dubbed") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
                                            )
                                        }
                                    }

                                    if (hindiDubbedKDramaList.isNotEmpty()) {
                                        item {
                                            MediaCategoryRowSection(
                                                title = " Hindi Dubbed K-Dramas",
                                                items = hindiDubbedKDramaList,
                                                onSeeAllClick = { viewModel.setSelectedMediaCategory("Hindi Dubbed K-Dramas") },
                                                onItemClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onPlayClick = { item -> viewModel.preScrapeMediaItem(item); selectedItemForDetail = item },
                                                onLoadMore = { viewModel.loadMoreMedia() }
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

        val allItems = ((mediaState as? UiState.Success<*>)?.data as? List<*>)?.filterIsInstance<MediaItem>() ?: emptyList()

        LaunchedEffect(activeWebPlayer) {
            activeWebPlayer?.item?.let { item ->
                viewModel.addMediaToHistory(item)
            }
        }

        // Active Cinemeta WebView Player Overlay
        activeWebPlayer?.let { state ->
            val imdbId = state.item.imdbId ?: "tt0111161"
            com.example.ui.components.CinemetaWebViewPlayer(
                imdbId = imdbId,
                title = state.item.title,
                type = state.item.type,
                season = state.season,
                episode = state.episode,
                allMediaItems = allItems,
                onSelectMedia = { selectedItem ->
                    viewModel.playMediaItem(selectedItem, 1, 1)
                                                        onNavigateToPlayer()
                },
                onClosePlayer = { activeWebPlayer = null },
                onFullScreenChange = { isFull ->
                    viewModel.setFullScreen(isFull)
                },
                modifier = Modifier.fillMaxSize(),
                isInPipMode = isInPipMode,
                onPlayingStateChanged = { playing ->
                    viewModel.setMediaPlaying(playing)
                },
                viewModel = viewModel,
                mediaItem = state.item
            )
        }
    }

    // Selected Media Item Details Modal
    selectedItemForDetail?.let { item ->
        val isFavorite by viewModel.isMediaFavoriteStream(item.id).collectAsState(initial = false)
        MediaDetailSheet(
            item = item,
            isFavorite = isFavorite,
            onFavoriteToggle = { viewModel.toggleMediaFavorite(item, isFavorite) },
            onDismiss = { selectedItemForDetail = null; autoPlayTrailerOnOpen = false },
            onPlayStream = { season, episode ->
                selectedItemForDetail = null
                autoPlayTrailerOnOpen = false
                viewModel.playMediaItem(item, season, episode)
                                                        onNavigateToPlayer()
            },
            viewModel = viewModel,
            startWithTrailer = autoPlayTrailerOnOpen
        )
    }

    // Beautiful Categories Grid Dialog
    if (showCategoryGridDialog) {
        Dialog(
            onDismissRequest = { viewModel.setShowCategoriesGrid(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isDark) SpaceBlack.copy(alpha = 0.98f) else Color(0xFFF5F5F7).copy(alpha = 0.98f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Categories",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = textColor
                                )
                                Text(
                                    text = "Select a genre or library to browse",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subTextColor
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.setShowCategoriesGrid(false) },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isDark) DeepSlate else Color.LightGray.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isDark) TextPrimary else Color.Black
                            )
                        }
                    }

                    // Grid of buttons
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(categoryDetails, key = { it.first }) { (name, icon, themeColor) ->
                            val isSelected = selectedCategory == name
                            Surface(
                                onClick = {
                                    if (name == "Live TV") {
                                        onNavigateToAirTab()
                                    } else {
                                        viewModel.setSelectedMediaCategory(name)
                                    }
                                    viewModel.setShowCategoriesGrid(false)
                                },
                                color = if (isDark) DeepSlate else Color.White,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) themeColor else (if (isDark) BorderColor else Color.LightGray.copy(alpha = 0.5f))
                                ),
                                shadowElevation = if (isSelected) 4.dp else 1.dp,
                                modifier = Modifier
                                    .height(90.dp)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    themeColor.copy(alpha = if (isSelected) 0.25f else 0.08f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .padding(14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(themeColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = themeColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isSelected) "Selected" else "View Genre",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 11.sp
                                                ),
                                                color = if (isSelected) themeColor else subTextColor
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

@Composable
fun MediaCategoryRowSection(
    title: String,
    items: List<MediaItem>,
    onSeeAllClick: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onLoadMore: (() -> Unit)? = null
) {
    val rowState = rememberLazyListState()

    LaunchedEffect(rowState) {
        snapshotFlow {
            val layoutInfo = rowState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItemsNumber - 3 && totalItemsNumber > 0
        }.collect { isNearEnd ->
            if (isNearEnd) {
                onLoadMore?.invoke()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                ),
                color = TextPrimary
            )
            TextButton(
                onClick = onSeeAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "See All (${items.size})",
                    fontSize = 12.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items = items, key = { index, item -> "${item.id}_$index" }) { index, item ->
                CompactMediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onPlayClick = { onPlayClick(item) }
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: MediaItem,
    viewModel: StreamViewModel,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cwCardScale"
    )
    val progressInfo = remember(item.id, item.imdbId) {
        viewModel.getMediaPlaybackProgress(item.imdbId ?: item.id)
    }
    val posMs = progressInfo.first
    val durMs = progressInfo.second
    val progressRatio = if (durMs > 0) (posMs.toFloat() / durMs.toFloat()).coerceIn(0.05f, 1f) else 0.45f
    val progressPercent = (progressRatio * 100).toInt()

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isPressed) NeonCyan else BorderColor),
        modifier = modifier
            .width(200.dp)
            .height(115.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    coroutineScope.launch {
                        delay(15)
                        onClick()
                    }
                }
            )
            .testTag("continue_watching_card_${item.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var isImageLoadError by remember(item.imageUrl) { mutableStateOf(false) }

            // Thumbnail image background
            if (!isImageLoadError && item.imageUrl.isNotBlank()) {
                val context = LocalContext.current


                val imageRequest = remember(item.imageUrl) {
                    ImageRequest.Builder(context)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .diskCacheKey(item.imageUrl)
                        .memoryCacheKey(item.imageUrl)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    onError = { isImageLoadError = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isImageLoadError || item.imageUrl.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2C1A30), Color(0xFF111827))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = LightAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Beautiful Gradient Scrim Overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            // Content Overlay (Title, Info & Progress percentage badge)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row with IMDb rating or Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small Category badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = item.category,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Rating Badge
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(8.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = item.rating,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom section (Title + watched percent / label status)
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.year.ifEmpty { "HD" },
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        // User progress label / status (e.g. "Completed" or "75% Watched")
                        Surface(
                            color = if (progressPercent >= 95) Color(0xFF2E7D32) else NeonCyan.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (progressPercent >= 95) "Watched" else "${progressPercent}% Watched",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Progress Line along card's bottom edge
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = if (progressPercent >= 95) Color(0xFF4CAF50) else NeonCyan,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun ContinueWatchingRowSection(
    title: String,
    items: List<MediaItem>,
    viewModel: StreamViewModel,
    onSeeAllClick: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit
) {
    val rowState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                ),
                color = TextPrimary
            )
            TextButton(
                onClick = onSeeAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "See All (${items.size})",
                    fontSize = 12.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items = items, key = { index, item -> "cw_${item.id}_$index" }) { index, item ->
                val progressInfo = remember(item.id, item.imdbId) {
                    viewModel.getMediaPlaybackProgress(item.imdbId ?: item.id)
                }
                ContinueWatchingCard(
                    item = item,
                    viewModel = viewModel,
                    onClick = { onItemClick(item) },
                    onPlayClick = {
                        viewModel.playMediaItem(item, startPositionMs = progressInfo.first)
                        onPlayClick(item)
                    }
                )
            }
        }
    }
}

@Composable
fun CompactMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "compactCardScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isPressed) NeonCyan else BorderColor),
        modifier = modifier
            .width(108.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    coroutineScope.launch {
                        delay(15) // Extremely fast 15ms delay to allow the ripple animation start frame to register immediately
                        onClick()
                    }
                }
            )
            .testTag("compact_media_card_${item.id}")
    ) {
        Column {
            var isImageLoadError by remember(item.imageUrl) { mutableStateOf(false) }
            val fallbackPoster = remember(item.title, item.type, item.category) {
                val isAnime = item.category.contains("Anime", ignoreCase = true) || item.type.equals("anime", ignoreCase = true)
                val isMovie = item.type.equals("movie", ignoreCase = true) || item.category.contains("Movie", ignoreCase = true)
                if (isAnime) {
                    if (isMovie) "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80"
                    else "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80"
                } else {
                    val defaultCinemaPosters = listOf(
                        "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&q=80",
                        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80",
                        "https://images.unsplash.com/photo-1518676599900-d28bb34aa5d1?w=500&q=80"
                    )
                    val idx = kotlin.math.abs(item.title.hashCode()) % defaultCinemaPosters.size
                    defaultCinemaPosters[idx]
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2C1A30), Color(0xFF111827))
                        )
                    )
            ) {
                if (item.imageUrl.isNotBlank() && !isImageLoadError) {
                    val context = LocalContext.current


                    val imageRequest = remember(item.imageUrl) {
                        ImageRequest.Builder(context)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                            .diskCacheKey(item.imageUrl)
                            .memoryCacheKey(item.imageUrl)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        onError = {
                            isImageLoadError = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback poster image or styled placeholder
                    val context = LocalContext.current


                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(fallbackPoster)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isImageLoadError && fallbackPoster.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = LightAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Premium VIP Badge Overlay Top Left
                if (item.isPremium) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "VIP",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                            .align(Alignment.TopStart)
                    )
                }

                // IMDb Rating Badge
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = item.rating,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Center Play Button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                        .background(NeonCyan.copy(alpha = 0.9f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.year,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                    Text(
                        text = if (item.type == "series") "Series" else "Movie",
                        color = NeonCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "mediaCardScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isPressed) NeonCyan else BorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    coroutineScope.launch {
                        delay(15) // Extremely fast 15ms delay to allow the ripple animation start frame to register immediately
                        onClick()
                    }
                }
            )
            .testTag("media_card_${item.id}")
    ) {
        Column {
            var isImageLoadError by remember(item.imageUrl) { mutableStateOf(false) }
            val fallbackPoster = remember(item.title, item.type, item.category) {
                val isAnime = item.category.contains("Anime", ignoreCase = true) || item.type.equals("anime", ignoreCase = true)
                val isMovie = item.type.equals("movie", ignoreCase = true) || item.category.contains("Movie", ignoreCase = true)
                if (isAnime) {
                    if (isMovie) "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80"
                    else "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80"
                } else {
                    val defaultCinemaPosters = listOf(
                        "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&q=80",
                        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80",
                        "https://images.unsplash.com/photo-1518676599900-d28bb34aa5d1?w=500&q=80"
                    )
                    val idx = kotlin.math.abs(item.title.hashCode()) % defaultCinemaPosters.size
                    defaultCinemaPosters[idx]
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2C1A30), Color(0xFF111827))
                        )
                    )
            ) {
                if (item.imageUrl.isNotBlank() && !isImageLoadError) {
                    val context = LocalContext.current


                    val imageRequest = remember(item.imageUrl) {
                        ImageRequest.Builder(context)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                            .diskCacheKey(item.imageUrl)
                            .memoryCacheKey(item.imageUrl)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        onError = { isImageLoadError = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val context = LocalContext.current


                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(fallbackPoster)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isImageLoadError && fallbackPoster.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = LightAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Premium VIP Badge Overlay
                if (item.isPremium) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "VIP",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                    )
                }

                // Rating Badge (moved to top-left and category name removed)
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = item.rating,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Center Stream Play Button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                        .background(NeonCyan.copy(alpha = 0.85f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Stream",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.episodes.isNotEmpty() && item.episodes != item.year) item.episodes else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = item.year,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealGoogleAccountsDialog(
    accounts: List<String>,
    onDismiss: () -> Unit,
    onAccountSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Google Account", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    Surface(
                        onClick = { onAccountSelected(account) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2C2C2E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(account, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1C1C1E)
    )
}

@Composable
fun ShimmerPosterRow(
    itemCount: Int = 4,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(itemCount) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2E))
            )
        }
    }
}

@Composable
fun ShimmerPosterGrid(
    columns: Int = 2,
    itemCount: Int = 6,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(itemCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2E))
            )
        }
    }
}

@Composable
fun WebBrowserDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Web Version", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text("Do you want to open Home Air TV in your external browser?", color = Color.LightGray) },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Text("Open Browser", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1C1C1E)
    )
}

@Composable
fun PrivacyPolicySectionCard(
    title: String,
    icon: ImageVector,
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberGray),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = text, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
        }
    }
}

@Composable
fun ArchivedChannelsSubPage(
    viewModel: StreamViewModel,
    onBack: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val cardBg = if (isDark) CyberGray else Color(0xFFF8F9FA)
    val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFF6B00).copy(alpha = 0.3f)
    val prefs by viewModel.channelPreferences.collectAsState()
    val archivedList = remember(prefs) { prefs.filter { it.isHidden } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFFF6B00))
            }
            Text("Archived Channels", color = Color(0xFFFF6B00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (archivedList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No archived channels found", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(archivedList) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(item.name, color = if (isDark) Color.White else Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(item.customGroup ?: "General", color = Color.Gray, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.toggleHideChannel(
                                        IptvChannel(name = item.name, url = item.url, logo = "", group = item.customGroup ?: "General"),
                                        hide = false
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Activate", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M3uPlaylistsManagerSubPage(
    viewModel: StreamViewModel,
    onBack: () -> Unit,
    onNavigateToAirTab: () -> Unit = {},
    m3uFileLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    nameInput: String,
    onNameChange: (String) -> Unit,
    urlInput: String,
    onUrlChange: (String) -> Unit
) {
    val customPlaylistsState by viewModel.customPlaylists.collectAsState()
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val cardBg = if (isDark) CyberGray else Color(0xFFF8F9FA)
    val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFF6B00).copy(alpha = 0.35f)
    val playlistItemBg = if (isDark) Color(0xFF2C2C2E) else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFFF6B00))
            }
            Text("M3U8 Playlists Manager", color = Color(0xFFFF6B00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // Section 1: URL Import
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Import from Link / URL", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = { Text("Playlist Name", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color(0xFFFF6B00),
                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E),
                        cursorColor = Color(0xFFFF6B00),
                        focusedLabelColor = Color(0xFFFF6B00)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChange,
                    label = { Text("M3U / M3U8 Link URL", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color(0xFFFF6B00),
                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E),
                        cursorColor = Color(0xFFFF6B00),
                        focusedLabelColor = Color(0xFFFF6B00)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = {
                        if (urlInput.isBlank()) {
                            Toast.makeText(context, "Please enter M3U URL", Toast.LENGTH_SHORT).show()
                        } else {
                            val pName = nameInput.ifBlank { "Custom Web Playlist" }
                            viewModel.addCustomPlaylistFromUrl(pName, urlInput) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    onNameChange("")
                                    onUrlChange("")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Playlist Link", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 2: File Import
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Upload Local M3U File", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                
                Button(
                    onClick = { m3uFileLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select .m3u / .m3u8 File", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 3: Playlists List
        Text("Your Local Playlists", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
        if (customPlaylistsState.isEmpty()) {
            Text("No custom playlists added yet", color = Color.Gray, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                customPlaylistsState.forEach { pl ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = playlistItemBg),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFFF6B00).copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            viewModel.selectPlaylist(com.example.data.model.IptvPlaylist(name = pl.name, url = "custom://${pl.id}", group = "Custom"))
                            onNavigateToAirTab()
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(pl.name, color = if (isDark) Color.White else Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (pl.source == "url") pl.pathOrUrl else "Local File Upload",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.deleteCustomPlaylist(pl.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalVideosManagerSubPage(
    viewModel: StreamViewModel,
    onBack: () -> Unit,
    multiPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    selectedQueueVideos: androidx.compose.runtime.snapshots.SnapshotStateList<java.io.File>,
    onPlayFile: (java.io.File) -> Unit
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val cardBg = if (isDark) CyberGray else Color(0xFFF8F9FA)
    val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFF6B00).copy(alpha = 0.35f)
    val fileCardBg = if (isDark) Color(0xFF2C2C2E) else Color.White

    // Discover offline videos from app storage directories
    val localFiles = remember(selectedQueueVideos.size) {
        val dirs = listOfNotNull(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
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
                        it.name.endsWith(".ts", true) ||
                        it.name.contains("video", true)
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        }.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFFF6B00))
            }
            Text("Local Offline Videos", color = Color(0xFFFF6B00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Playlist Queue Tools", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Select multiple videos from your device to save them in your offline playlist library.", color = Color.Gray, fontSize = 12.sp)
                Button(
                    onClick = { multiPickerLauncher.launch("video/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Multi Videos to Playlist", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Offline Library", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (selectedQueueVideos.isNotEmpty()) {
                TextButton(onClick = { selectedQueueVideos.clear() }) {
                    Text("Clear Selection (${selectedQueueVideos.size})", color = Color.Red, fontSize = 12.sp)
                }
            }
        }

        if (localFiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No offline videos imported yet", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(localFiles) { file ->
                        val isSelected = selectedQueueVideos.any { it.absolutePath == file.absolutePath }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) NeonCyan.copy(alpha = 0.12f) else fileCardBg),
                            border = BorderStroke(1.dp, if (isSelected) NeonCyan else (if (isDark) Color.Transparent else Color(0xFFFF6B00).copy(alpha = 0.25f))),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedQueueVideos.add(file)
                                            } else {
                                                selectedQueueVideos.removeAll { it.absolutePath == file.absolutePath }
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan, checkmarkColor = Color.Black)
                                    )
                                    Column {
                                        Text(file.nameWithoutExtension, color = if (isDark) Color.White else Color(0xFFFF6B00), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = "${file.length() / (1024 * 1024)} MB | Offline Playlist", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                                
                                Button(
                                    onClick = { onPlayFile(file) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Floating Action Bar if multiple videos are selected
                if (selectedQueueVideos.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Button(
                            onClick = { onPlayFile(selectedQueueVideos.first()) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.padding(horizontal = 32.dp).height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Selection Queue (${selectedQueueVideos.size} Videos)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) Color.LightGray else Color(0xFF6C6C70)
    val borderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

    val supportLinks = listOf(
        SupportItem("Telegram Community", "https://t.me/homeaircommunity", Icons.Outlined.Send, Color(0xFF0088CC)),
        SupportItem("Telegram Group", "https://t.me/HomeAirTv", Icons.Outlined.Group, Color(0xFF2AABEE)),
        SupportItem("Facebook", "https://www.facebook.com/HomeAirTv", Icons.Outlined.Share, Color(0xFF1877F2)),
        SupportItem("Email", "mailto:hmairtv@gmail.com", Icons.Outlined.Email, Color(0xFFEA4335)),
        SupportItem("Messenger", "https://m.me/homeairtv", Icons.Outlined.Chat, Color(0xFF0084FF))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SupportAgent,
                    contentDescription = "Support",
                    tint = Color(0xFFFF6B00),
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Support & Community",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "Connect with us on official channels",
                        fontSize = 12.sp,
                        color = subTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            supportLinks.forEach { item ->
                Surface(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(item.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = item.color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = subTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class SupportItem(
    val title: String,
    val url: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUsBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) Color.LightGray else Color(0xFF6C6C70)
    val borderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF6B00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tv,
                            contentDescription = "App Icon",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "HOME AIR TV",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "Smart Media Streaming Application",
                            fontSize = 12.sp,
                            color = subTextColor
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Overview",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "HOME AIR TV is an open-source, all-in-one smart media streaming application developed by volunteer developers under Xubilas Web Dev Corp. Built for Android devices and distributed via Xubilas Apps Hub, it offers free, unlimited access to global movies, TV shows, anime, and live broadcast channels.",
                            fontSize = 13.sp,
                            color = subTextColor,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Key Highlights",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        val highlights = listOf(
                            "Open-Source & Ad-Free Architecture",
                            "4K VidSrc & Multi-Server Player Engine",
                            "Auto Picture-in-Picture (PiP) Mode",
                            "Global Live TV Channels & FanCode VIP Pass",
                            "Multi-Language Subtitles & Audio Engine"
                        )
                        
                        highlights.forEach { highlight ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = highlight,
                                    fontSize = 12.sp,
                                    color = textColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Developer & Publisher",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Xubilas Web Dev Corp / Volunteer Community",
                            fontSize = 12.sp,
                            color = subTextColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://homeairtv.xubilaswebdevcorp.shop"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Visit Official Website", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiringFeedScreen(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    isHeaderVisible: Boolean,
    modifier: Modifier = Modifier,
    onBackPress: (() -> Unit)? = null
) {
    val currentFeedState by viewModel.airingMergedState.collectAsState()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Trigger initial load on entry if empty
    LaunchedEffect(Unit) {
        viewModel.loadMergedAiringFeed()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            // Initial Loading
            currentFeedState.isLoading && currentFeedState.reels.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF6B00),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Loading reels...",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error State (with empty list)
            currentFeedState.error != null && currentFeedState.reels.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentFeedState.error ?: "Unable to load feed. Please try again.",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retryMergedAiringFeed() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Success / Showing Reels
            else -> {
                com.example.ui.components.ShortReelsPager(
                    reels = currentFeedState.reels,
                    isLoadingMore = currentFeedState.isLoadingMore,
                    hasMore = currentFeedState.hasMore,
                    onLoadMore = { viewModel.loadMoreMergedAiringFeed() },
                    modifier = Modifier.fillMaxSize(),
                    onBackPress = onBackPress
                )
            }
        }
    }
}

