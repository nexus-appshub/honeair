package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.NeonCyan

@Composable
fun YouTubeTrailerModal(
    showModal: Boolean,
    onDismiss: () -> Unit,
    title: String,
    trailerId: String?,
    isLoading: Boolean = false
) {
    if (!showModal) return

    val context = LocalContext.current
    var isWebpagePlayerMode by remember { mutableStateOf(false) }
    var keyReload by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .border(1.dp, Color(0xFF2C2C38), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121218))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFFFF0000).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "YOUTUBE TRAILER",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF4D4D),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (isWebpagePlayerMode) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = NeonCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "WEBPAGE MODE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Player Container (16:9 Aspect Ratio Box)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading || trailerId.isNullOrBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = NeonCyan,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Loading Trailer...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            key(keyReload, isWebpagePlayerMode, trailerId) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                            setBackgroundColor(android.graphics.Color.BLACK)
                                            webChromeClient = object : WebChromeClient() {
                                                override fun getDefaultVideoPoster(): Bitmap? {
                                                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                                }
                                            }
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                    val reqUrl = request?.url?.toString() ?: return false
                                                    if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                                        return false
                                                    }
                                                    return true
                                                }
                                            }
                                            settings.apply {
                                                javaScriptEnabled = true
                                                mediaPlaybackRequiresUserGesture = false
                                                domStorageEnabled = true
                                                databaseEnabled = true
                                                useWideViewPort = true
                                                loadWithOverviewMode = true
                                                allowFileAccess = true
                                                allowContentAccess = true
                                                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                }
                                            }

                                            if (isWebpagePlayerMode) {
                                                loadUrl("https://m.youtube.com/watch?v=$trailerId")
                                            } else {
                                                val htmlData = getYouTubeEmbedHtml(trailerId)
                                                loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Player Mode Switchers & Actions Row
                    if (!trailerId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Embed / Webpage Mode Toggle Pill
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isWebpagePlayerMode = !isWebpagePlayerMode
                                        keyReload++
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isWebpagePlayerMode) NeonCyan.copy(alpha = 0.2f) else Color(0xFF22222C),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isWebpagePlayerMode) NeonCyan else Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isWebpagePlayerMode) Icons.Default.Language else Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = if (isWebpagePlayerMode) NeonCyan else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isWebpagePlayerMode) "Embed Mode" else "Webpage Mode",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isWebpagePlayerMode) NeonCyan else Color.White
                                    )
                                }
                            }

                            // Open in External YouTube App
                            Surface(
                                modifier = Modifier.clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailerId"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFF0000).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Open YouTube",
                                        tint = Color(0xFFFF4D4D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "YouTube App",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFF4D4D)
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

fun getYouTubeEmbedHtml(videoId: String): String {
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000000; }
            html, body { width: 100%; height: 100%; overflow: hidden; background: #000000; display: flex; align-items: center; justify-content: center; }
            .video-container { position: relative; width: 100%; height: 100%; background: #000000; }
            iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }
        </style>
    </head>
    <body>
        <div class="video-container">
            <iframe id="player" type="text/html" width="100%" height="100%"
                src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&enablejsapi=1&rel=0&modestbranding=1"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen>
            </iframe>
        </div>
    </body>
    </html>
    """.trimIndent()
}
