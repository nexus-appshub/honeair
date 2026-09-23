package com.example.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.download.MediaDownloader
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class QualityOptionItem(
    val qualityKey: String,
    val title: String,
    val badge: String,
    val badgeColor: Color,
    val borderColor: Color,
    val estimatedSize: String,
    val subText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderModal(
    showModal: Boolean,
    onDismiss: () -> Unit,
    title: String,
    imdbId: String,
    season: Int = 1,
    episode: Int = 1,
    isSeries: Boolean = false,
    isAnime: Boolean = false,
    capturedVideoUrl: String? = null,
    coroutineScope: CoroutineScope,
    userProfile: com.example.ui.viewmodel.UserProfile? = null,
    viewModel: com.example.ui.viewmodel.StreamViewModel? = null
) {
    if (!showModal) return

    val context = LocalContext.current
    val isEffectiveAnime = isAnime || imdbId.startsWith("anikoto_") || title.contains("anime", ignoreCase = true)
    var preferDubForAnime by remember { mutableStateOf(false) }
    var animeDownloadInfo by remember { mutableStateOf<com.example.download.AnimeEpisodeDownloadInfo?>(null) }
    var activeQualityForChoice by remember { mutableStateOf<com.example.download.AnimeQualityOption?>(null) }
    var isAnimeScrapingQualities by remember { mutableStateOf(false) }

    LaunchedEffect(isEffectiveAnime, preferDubForAnime, showModal, title, season, episode) {
        if (isEffectiveAnime && showModal) {
            isAnimeScrapingQualities = true
            try {
                val info = com.example.download.AnimeDownloader.resolveAnimeDownloadOptions(
                    context = context,
                    title = title,
                    season = season,
                    episode = episode,
                    preferDub = preferDubForAnime
                )
                animeDownloadInfo = info
            } catch (e: Exception) {
                android.util.Log.e("DownloaderModal", "Anime resolve error: ${e.message}")
            } finally {
                isAnimeScrapingQualities = false
            }
        }
    }

    // Launcher for Notification & Storage permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val postNotifGranted = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: true
        val writeStorageGranted = if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            permissions[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: true
        } else {
            true
        }
        
        if (postNotifGranted && writeStorageGranted) {
            Toast.makeText(context, "Permissions enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic Permission Checker Helper
    val checkAndRequestPermissions: (() -> Unit) -> Unit = { onGranted ->
        val permissionsToRequest = mutableListOf<String>()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            onGranted()
        }
    }

    // Auto-request permission as soon as the download modal is opened
    LaunchedEffect(Unit) {
        checkAndRequestPermissions {}
    }

    val cleanImdb = if (imdbId.isBlank()) "tt1375666" else imdbId

    val sStr = if (season < 10) "0$season" else "$season"
    val eStr = if (episode < 10) "0$episode" else "$episode"
    val searchQuery = if (isSeries) "$title S${sStr}E${eStr}" else title

    val videoDownloaderUrl = "https://videodownloader.site/"

    val movieDownloader02Url = if (isSeries) {
        "https://02moviedownloader.site/api/download/tv/$cleanImdb/$season/$episode"
    } else {
        "https://02moviedownloader.site/api/download/movie/$cleanImdb"
    }

    val vidsrcUrl = if (isSeries) {
        "https://vidsrc.me/embed/tv?imdb=$cleanImdb&season=$season&episode=$episode"
    } else {
        "https://vidsrc.me/embed/movie?imdb=$cleanImdb"
    }

    val directMediaDownloaderUrl = if (isSeries) {
        "https://vidsrc.net/embed/tv?imdb=$cleanImdb&season=$season&episode=$episode"
    } else {
        "https://vidsrc.net/embed/movie?imdb=$cleanImdb"
    }

    var selectedWebUrl by remember { mutableStateOf<String?>(null) }
    var selectedWebTitle by remember { mutableStateOf("") }
    
    var hlsQualities by remember { mutableStateOf<List<MediaDownloader.HlsQuality>>(emptyList()) }
    var isCheckingQualities by remember { mutableStateOf(false) }
    var showQualitySelectionDialog by remember { mutableStateOf(false) }
    var isFetchingQualities by remember { mutableStateOf(false) }
    var isResolvingOption1 by remember { mutableStateOf(false) }
    var activeResolvedStreamUrl by remember { mutableStateOf<String?>(null) }
    var activeResolvedReferer by remember { mutableStateOf<String?>(null) }
    var detectedHlsQualities by remember { mutableStateOf<List<MediaDownloader.HlsQuality>>(emptyList()) }

    val fallbackDirectUrl = if (isSeries) "https://02moviedownloader.site/api/download/tv/$cleanImdb/$season/$episode" else "https://02moviedownloader.site/api/download/movie/$cleanImdb"
    val effectiveCapturedUrl = if (!capturedVideoUrl.isNullOrBlank()) capturedVideoUrl else fallbackDirectUrl

    if (showQualitySelectionDialog) {
        val estimated1080 = if (isSeries) "~650 MB" else "~1.4 GB"
        val estimated720 = if (isSeries) "~380 MB" else "~750 MB"
        val estimated480 = if (isSeries) "~210 MB" else "~380 MB"
        val estimated360 = if (isSeries) "~110 MB" else "~190 MB"

        val qualityItems = listOf(
            QualityOptionItem(
                qualityKey = "1080p",
                title = "1080p Full HD",
                badge = "BEST QUALITY",
                badgeColor = Color(0xFFFFB74D),
                borderColor = Color(0xFFFFB74D).copy(alpha = 0.5f),
                estimatedSize = estimated1080,
                subText = "Ultra High Definition • 1920x1080"
            ),
            QualityOptionItem(
                qualityKey = "720p",
                title = "720p HD",
                badge = "RECOMMENDED",
                badgeColor = NeonCyan,
                borderColor = NeonCyan.copy(alpha = 0.6f),
                estimatedSize = estimated720,
                subText = "High Definition • 1280x720"
            ),
            QualityOptionItem(
                qualityKey = "480p",
                title = "480p SD",
                badge = "DATA SAVER",
                badgeColor = Color(0xFF81C784),
                borderColor = Color(0xFF81C784).copy(alpha = 0.5f),
                estimatedSize = estimated480,
                subText = "Standard Definition • 854x480"
            ),
            QualityOptionItem(
                qualityKey = "360p",
                title = "360p Fast",
                badge = "SMALLEST SIZE",
                badgeColor = Color(0xFF90CAF9),
                borderColor = Color(0xFF90CAF9).copy(alpha = 0.4f),
                estimatedSize = estimated360,
                subText = "Low Quality • 640x360"
            )
        )

        AlertDialog(
            onDismissRequest = { showQualitySelectionDialog = false },
            containerColor = DeepSlate,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFF6B00).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFFFF6B00).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Select Download Quality",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = title + if (isSeries) " (S${season}E${episode})" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isFetchingQualities) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Analyzing quality streams...",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    qualityItems.forEach { item ->
                        Surface(
                            onClick = {
                                val sanitizedTitle = title.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
                                val fileName = if (isSeries) {
                                    "${sanitizedTitle}_S${season}E${episode}_${item.qualityKey}.mp4"
                                } else {
                                    "${sanitizedTitle}_${item.qualityKey}.mp4"
                                }

                                val matchedHls = when (item.qualityKey) {
                                    "1080p" -> detectedHlsQualities.find { it.resolution.contains("1080") || it.resolution.contains("1920") }
                                        ?: detectedHlsQualities.firstOrNull()
                                    "720p" -> detectedHlsQualities.find { it.resolution.contains("720") || it.resolution.contains("1280") }
                                        ?: (if (detectedHlsQualities.size > 1) detectedHlsQualities[1] else detectedHlsQualities.firstOrNull())
                                    "480p" -> detectedHlsQualities.find { it.resolution.contains("480") || it.resolution.contains("854") }
                                        ?: (if (detectedHlsQualities.size > 2) detectedHlsQualities[2] else detectedHlsQualities.lastOrNull())
                                    "360p" -> detectedHlsQualities.find { it.resolution.contains("360") || it.resolution.contains("640") }
                                        ?: detectedHlsQualities.lastOrNull()
                                    else -> null
                                }

                                val streamToDownload = activeResolvedStreamUrl ?: effectiveCapturedUrl
                                val downloadUrl = matchedHls?.url ?: streamToDownload
                                val uAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                val defaultRef = if (downloadUrl.contains("vidsrc")) "https://vidsrc.me/" else if (downloadUrl.contains("02movie")) "https://02moviedownloader.site/" else null
                                val ref = activeResolvedReferer ?: defaultRef

                                MediaDownloader.downloadFile(
                                    context = context,
                                    url = downloadUrl,
                                    fileName = fileName,
                                    coroutineScope = coroutineScope,
                                    userAgent = uAgent,
                                    referer = ref,
                                    fallbackUrl = streamToDownload
                                )

                                Toast.makeText(
                                    context,
                                    "Starting ${item.qualityKey} download (${item.estimatedSize})",
                                    Toast.LENGTH_SHORT
                                ).show()

                                showQualitySelectionDialog = false
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, item.borderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(item.badgeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .border(1.dp, item.badgeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.qualityKey,
                                            color = item.badgeColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                ),
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(item.badgeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = item.badge,
                                                    color = item.badgeColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 8.sp,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.subText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = item.estimatedSize,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        ),
                                        color = NeonCyan
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQualitySelectionDialog = false }) {
                    Text("Cancel", color = Color.Gray, fontSize = 13.sp)
                }
            }
        )
    }

    if (selectedWebUrl != null) {
        DownloaderWebViewModal(
            title = "$title (${if (isSeries) "S${season}E${episode}" else "Movie"})",
            searchQuery = searchQuery,
            initialUrl = selectedWebUrl!!,
            videoDownloaderSiteUrl = videoDownloaderUrl,
            movieDownloader02SiteUrl = movieDownloader02Url,
            vidsrcSiteUrl = vidsrcUrl,
            directMediaDownloaderSiteUrl = directMediaDownloaderUrl,
            onClose = { selectedWebUrl = null }
        )
    }

    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpaceBlack,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            if (userProfile == null) {
                // Sign In Required to Download View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔒 Sign In Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepSlate, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.2f), NeonMagenta.copy(alpha = 0.2f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Sign in to Download",
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Sign in with Google to Download",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "You must be signed in with your Google account before downloading '${title}'. Sign in to unlock fast multi-server downloads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel?.signInWithGoogle(
                                context = context,
                                onSuccess = {
                                    Toast.makeText(context, "Signed in successfully! You can now download.", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OfficialGoogleLogo(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else if (isEffectiveAnime) {
                // Dedicated Anime Downloader Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFF7C4DFF)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎌", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Anime Downloader",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                            Text(
                                text = "$title • S${season}E${episode}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SUB / DUB Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { preferDubForAnime = false },
                        shape = RoundedCornerShape(10.dp),
                        color = if (!preferDubForAnime) NeonCyan.copy(alpha = 0.25f) else Color.Transparent,
                        border = if (!preferDubForAnime) BorderStroke(1.dp, NeonCyan) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🇯🇵 SUB (Original + Subs)",
                                color = if (!preferDubForAnime) NeonCyan else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Surface(
                        onClick = { preferDubForAnime = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (preferDubForAnime) Color(0xFFFFB74D).copy(alpha = 0.25f) else Color.Transparent,
                        border = if (preferDubForAnime) BorderStroke(1.dp, Color(0xFFFFB74D)) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎙️ DUB (English Dubbed)",
                                color = if (preferDubForAnime) Color(0xFFFFB74D) else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isAnimeScrapingQualities) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Extracting high-speed Anime streams...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bypassing stream token expiration & resolving all qualities",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (animeDownloadInfo != null && animeDownloadInfo!!.qualities.isNotEmpty()) {
                    val info = animeDownloadInfo!!

                    // Server Info Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scraped Server: ${info.serverName}",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = if (info.isDub) "English Dub" else "Japanese Sub",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Available Qualities List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (quality in info.qualities) {
                            val badgeColor = when (quality.resolution) {
                                "1080p" -> Color(0xFFFFB74D)
                                "720p" -> NeonCyan
                                "480p" -> Color(0xFFB388FF)
                                else -> Color(0xFF80D8FF)
                            }

                            Surface(
                                onClick = {
                                    activeQualityForChoice = quality
                                },
                                color = if (isDarkTheme) DeepSlate else Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                                .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = quality.resolution,
                                                color = badgeColor,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = quality.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    ),
                                                    color = TextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = quality.badge,
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 8.sp,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Direct Stream • AES-128 Decrypted",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = quality.estimatedSize,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp
                                            ),
                                            color = NeonCyan
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                                .padding(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download Anime",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // No direct qualities fallback
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Could not extract direct stream qualities",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try switching between SUB / DUB or retry scraping",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                isAnimeScrapingQualities = true
                                coroutineScope.launch {
                                    try {
                                        animeDownloadInfo = com.example.download.AnimeDownloader.resolveAnimeDownloadOptions(
                                            context = context,
                                            title = title,
                                            season = season,
                                            episode = episode,
                                            preferDub = preferDubForAnime
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isAnimeScrapingQualities = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry Anime Scraping", color = SpaceBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (hlsQualities.isNotEmpty()) {
                // Quality Selection View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { hlsQualities = emptyList() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select Download Quality",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                for (quality in hlsQualities) {
                    Surface(
                        onClick = {
                            val sanitizedTitle = title.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
                            val fileName = if (isSeries) "${sanitizedTitle}_S${season}E${episode}_${quality.resolution}.mp4" else "${sanitizedTitle}_${quality.resolution}.mp4"
                            
                            val uAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            val ref = if (quality.url.contains("vidsrc")) "https://vidsrc.me/" else if (quality.url.contains("02movie")) "https://02moviedownloader.site/" else null

                            MediaDownloader.downloadFile(
                                context = context,
                                url = quality.url,
                                fileName = fileName,
                                coroutineScope = coroutineScope,
                                userAgent = uAgent,
                                referer = ref,
                                fallbackUrl = effectiveCapturedUrl
                            )
                            onDismiss()
                        },
                        color = if (isDarkTheme) DeepSlate else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HighQuality, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(quality.resolution, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.Download, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else if (isCheckingQualities) {
                // Loading Qualities View
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fetching available qualities...", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                // Original Selection View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Download Media Options",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = title + if (isSeries) " (Season $season Episode $episode)" else "",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SELECT DOWNLOADER SERVER / QUALITY",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val fallbackDirectUrl = if (isSeries) "https://02moviedownloader.site/api/download/tv/$cleanImdb/$season/$episode" else "https://02moviedownloader.site/api/download/movie/$cleanImdb"
                val effectiveCapturedUrl = if (!capturedVideoUrl.isNullOrBlank()) capturedVideoUrl else fallbackDirectUrl

                // Row 1: 4 circular option buttons in 1 line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    // Option 1: Direct-Now (Deep Orange)
                    CircularOptionButton(
                        title = "Option 1",
                        badgeText = if (isResolvingOption1) "Loading..." else "Direct-Now",
                        icon = Icons.Default.PlayForWork,
                        isDeepOrange = true,
                        onClick = {
                            checkAndRequestPermissions {
                                coroutineScope.launch {
                                    isResolvingOption1 = true
                                    var directStreamUrl = capturedVideoUrl
                                    var streamReferer: String? = null

                                    val isCapturedDirectStream = !directStreamUrl.isNullOrBlank() &&
                                        !directStreamUrl.contains("02moviedownloader.site") &&
                                        !directStreamUrl.contains("videodownloader.site") &&
                                        !directStreamUrl.startsWith("native://")

                                    if (!isCapturedDirectStream) {
                                        try {
                                            val isAnime = title.contains("anime", ignoreCase = true) || imdbId.startsWith("anikoto_")
                                            val streamResult = com.example.scraper.UnifiedStreamManager.getStream(
                                                context = context,
                                                title = title,
                                                tmdbId = cleanImdb,
                                                isTv = isSeries,
                                                season = season,
                                                episode = episode,
                                                isAnime = isAnime
                                            )
                                            if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                                                directStreamUrl = streamResult.streamUrl
                                                streamReferer = streamResult.referer ?: streamResult.headers["Referer"] ?: streamResult.headers["referer"]
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    isResolvingOption1 = false

                                    if (!directStreamUrl.isNullOrBlank() && !directStreamUrl.contains("02moviedownloader.site") && !directStreamUrl.contains("videodownloader.site")) {
                                        activeResolvedStreamUrl = directStreamUrl
                                        activeResolvedReferer = streamReferer
                                        showQualitySelectionDialog = true
                                        if (directStreamUrl.lowercase().contains("m3u8")) {
                                            isFetchingQualities = true
                                            val fetched = MediaDownloader.getHlsQualities(directStreamUrl, referer = streamReferer)
                                            detectedHlsQualities = fetched
                                            isFetchingQualities = false
                                        }
                                    } else {
                                        // Seamlessly open in-app web downloader with direct download
                                        selectedWebTitle = "Option 1 - Direct Downloader"
                                        selectedWebUrl = movieDownloader02Url
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Option 2
                    CircularOptionButton(
                        title = "Option 2",
                        badgeText = "HD Search",
                        icon = Icons.Default.HighQuality,
                        onClick = {
                            checkAndRequestPermissions {
                                selectedWebTitle = "Option 2"
                                selectedWebUrl = videoDownloaderUrl
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Option 3
                    CircularOptionButton(
                        title = "Option 3",
                        badgeText = "Fast In-App",
                        icon = Icons.Default.Speed,
                        onClick = {
                            checkAndRequestPermissions {
                                selectedWebTitle = "Option 3"
                                selectedWebUrl = movieDownloader02Url
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Option 4
                    CircularOptionButton(
                        title = "Option 4",
                        badgeText = "VidSrc Mirror",
                        icon = Icons.Default.CloudDownload,
                        onClick = {
                            checkAndRequestPermissions {
                                selectedWebTitle = "Option 4"
                                selectedWebUrl = vidsrcUrl
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2: Option 5
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    CircularOptionButton(
                        title = "Option 5",
                        badgeText = "Direct Media",
                        icon = Icons.Default.Bolt,
                        onClick = {
                            checkAndRequestPermissions {
                                selectedWebTitle = "Option 5"
                                selectedWebUrl = directMediaDownloaderUrl
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.25f)
                    )
                }
            }
        }
    }

    if (activeQualityForChoice != null) {
        val quality = activeQualityForChoice!!
        val directUrl: String? = com.example.download.AnimeDownloader.getDirectDownloadUrl(quality.streamUrl, quality.resolution)
        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

        AlertDialog(
            onDismissRequest = { activeQualityForChoice = null },
            title = {
                Text(
                    text = "Download Ready 🚀",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "File: ${title.replace(Regex("[^A-Za-z0-9 ]"), "")}_S${season}E${episode}_${quality.resolution}.mp4",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Estimated Size: ${quality.estimatedSize}",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select your preferred download method below:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (directUrl != null) {
                        Button(
                            onClick = {
                                com.example.download.AnimeDownloader.startNativeDirectDownload(
                                    context = context,
                                    animeTitle = title,
                                    season = season,
                                    episode = episode,
                                    quality = quality.resolution,
                                    directUrl = directUrl
                                )
                                activeQualityForChoice = null
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Direct High-Speed (MP4)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            checkAndRequestPermissions {
                                com.example.download.AnimeDownloader.startAnimeDownload(
                                    context = context,
                                    animeTitle = title,
                                    season = season,
                                    episode = episode,
                                    qualityOption = quality,
                                    coroutineScope = coroutineScope
                                )
                                Toast.makeText(
                                    context,
                                    "Downloading $title ${quality.resolution} (${quality.estimatedSize})",
                                    Toast.LENGTH_SHORT
                                ).show()
                                activeQualityForChoice = null
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDarkTheme) DeepSlate else Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Standard Segmented HLS", color = TextPrimary)
                    }

                    if (directUrl != null) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directUrl.toString()))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                activeQualityForChoice = null
                                onDismiss()
                            },
                            border = BorderStroke(1.dp, NeonMagenta),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = NeonMagenta)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Link in Browser", color = NeonMagenta)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { activeQualityForChoice = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = if (isDarkTheme) SpaceBlack else Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun CircularOptionButton(
    title: String,
    badgeText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDeepOrange: Boolean = false
) {
    val surfaceColor = if (isDeepOrange) Color(0xFF5C1D00) else Color(0xFF0A3D24)
    val borderColor = if (isDeepOrange) Color(0xFFFF6D00) else Color(0xFF00E676)
    val gradientColors = if (isDeepOrange) listOf(Color(0xFFE65100), Color(0xFFBF360C)) else listOf(Color(0xFF145A38), Color(0xFF062B18))
    val iconTint = if (isDeepOrange) Color(0xFFFFD54F) else Color(0xFF00FF87)
    val badgeColor = if (isDeepOrange) Color(0xFFFFCC80) else Color(0xFF80E8B0)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = surfaceColor,
            border = BorderStroke(1.5.dp, borderColor),
            shadowElevation = 8.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = gradientColors
                        )
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = badgeText,
            color = badgeColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DownloaderOptionCard(
    title: String,
    badgeText: String,
    badgeColor: Color,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DeepSlate,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Helpers for bulletproof media title and season/episode extraction
private fun isNoiseOrHashOrInvalidTitle(str: String?): Boolean {
    if (str.isNullOrBlank()) return true
    val trimmed = str.trim()
    if (trimmed.length < 2) return true
    val lower = trimmed.lowercase()

    // 1. Long hex strings, MD5, SHA, UUID, or random hash IDs (e.g. 295a796c44418f4977f0417db64c5357)
    if (lower.matches(Regex("^[0-9a-f]{8,}$"))) return true
    if (lower.matches(Regex(".*[0-9a-f]{12,}.*"))) return true
    if (lower.matches(Regex("^[0-9a-zA-Z_-]{16,}$")) && !lower.contains(" ")) return true
    if (lower.matches(Regex("^[0-9]+$"))) return true

    // Check random hash tokens without spaces
    if (!lower.contains(" ") && lower.length >= 8 && lower.any { it.isDigit() } && lower.count { it.isLetter() } >= 3) {
        val vowelCount = lower.count { it in "aeiou" }
        if (vowelCount <= 1) return true
    }

    // 2. Generic Noise Phrases
    val noisePhrases = listOf(
        "omnisandbox", "videodownloader", "02moviedownloader", "02movie", "vidsrc",
        "free online hd", "hd search", "fast in-app", "direct media", "media_download",
        "media download", "download media", "homeairtv", "download video", "free download",
        "video downloader", "movie downloader", "watch online free", "free online hd mp4",
        "online hd mp4 downloads", "streamtape", "mixdrop", "doodstream", "filelions",
        "from youtube", "youtube", "instagram", "tiktok", "facebook", "twitter",
        "from youtube instagram more", "youtube instagram more", "download options",
        "select episode", "select quality", "bulk select", "download videos", "download from",
        "video er hd mp4", "free online hd mp4 downloads", "media_download", "video player",
        "player", "stream", "download", "downloads", "direct download", "fast download",
        "hd mp4", "mp4 download", "video er", "online hd", "hd search"
    )
    if (noisePhrases.any { lower == it || (lower.contains(it) && trimmed.length < 80) }) {
        return true
    }

    return false
}

private fun cleanMediaTitle(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var s = raw.trim()

    if (isNoiseOrHashOrInvalidTitle(s)) return ""

    // URL decode if needed
    try {
        if (s.contains("%")) {
            s = java.net.URLDecoder.decode(s, "UTF-8")
        }
    } catch (e: Exception) {}

    // Replace fractions
    s = s.replace("½", " ")
        .replace("⅓", " ")
        .replace("¼", " ")
        .replace("¾", " ")

    // Strip web extensions if any
    s = s.replace(Regex("(?i)\\.(mp4|mkv|avi|webm|ts|m3u8|mov)$"), "")

    // Strip common release tags and codecs
    s = s.replace(Regex("(?i)\\b(1080p|720p|480p|360p|2160p|4k|hd|fhd|uhd|webrip|web-dl|bluray|x264|x265|hevc|aac|h264|h265|dvdrip|brrip|repack|yify|galaxy|eztv|rarbg)\\b"), "")

    // Strip common noise words at boundaries
    s = s.replace(Regex("(?i)\\b(watch|online|free|download|downloads|full|stream|streaming|omnisandbox|videodownloader|02moviedownloader|site|youtube|instagram|tiktok|facebook|twitter)\\b"), "")

    // Strip season ranges like S1-S2, Season 1-2, etc.
    s = s.replace(Regex("(?i)\\b[sS]\\d+\\s*-\\s*[sS]?\\d+\\b"), "")
    s = s.replace(Regex("(?i)\\bseason\\s*\\d+\\s*-\\s*\\d+\\b"), "")

    // Strip season and episode tags from the base title to prevent repetition
    s = s.replace(Regex("(?i)\\b[sS]\\d+\\s*[eE]\\d+\\b"), "")
    s = s.replace(Regex("(?i)\\bseason\\s*\\d+\\s*episode\\s*\\d+\\b"), "")
    s = s.replace(Regex("(?i)\\bseason\\s*\\d+\\b"), "")
    s = s.replace(Regex("(?i)\\bepisode\\s*\\d+\\b"), "")
    s = s.replace(Regex("(?i)\\bep\\.?\\s*\\d+\\b"), "")

    // Keep clean brackets for language tags like [Hindi] -> Hindi
    s = s.replace("[", " ").replace("]", " ")

    // Replace punctuation with spaces
    s = s.replace(Regex("[^A-Za-z0-9 ]"), " ")
    s = s.replace(Regex("\\s+"), " ").trim()

    if (isNoiseOrHashOrInvalidTitle(s)) return ""

    // Capitalize words nicely
    val words = s.split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    val result = words.joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }
    return if (isNoiseOrHashOrInvalidTitle(result)) "" else result
}

private fun parseSeasonAndEpisodeFromInputs(vararg inputs: String?): Pair<Int?, Int?> {
    val s0e0Regex = Regex("(?i)\\b[sS](\\d+)[eE](\\d+)\\b")
    val seasonEpRegex = Regex("(?i)\\b(?:season|s)\\s*(\\d+)\\s*(?:episode|ep|e)\\s*(\\d+)\\b")
    val tvSlashRegex = Regex("/(?:tv|series)/[^/]+/(\\d+)/(\\d+)")
    val epOnlyRegex = Regex("(?i)\\b(?:episode|ep)\\.?\\s*(\\d+)\\b")

    for (inp in inputs) {
        if (inp.isNullOrBlank()) continue

        tvSlashRegex.find(inp)?.let {
            val s = it.groupValues[1].toIntOrNull()
            val e = it.groupValues[2].toIntOrNull()
            if (s != null && e != null) return Pair(s, e)
        }

        s0e0Regex.find(inp)?.let {
            val s = it.groupValues[1].toIntOrNull()
            val e = it.groupValues[2].toIntOrNull()
            if (s != null && e != null) return Pair(s, e)
        }

        seasonEpRegex.find(inp)?.let {
            val s = it.groupValues[1].toIntOrNull()
            val e = it.groupValues[2].toIntOrNull()
            if (s != null && e != null) return Pair(s, e)
        }
    }

    // Check episode-only
    for (inp in inputs) {
        if (inp.isNullOrBlank()) continue
        epOnlyRegex.find(inp)?.let {
            val e = it.groupValues[1].toIntOrNull()
            if (e != null) return Pair(1, e)
        }
    }

    return Pair(null, null)
}

private fun extractTitleFromUrl(url: String?): String {
    if (url.isNullOrBlank()) return ""
    try {
        val uri = android.net.Uri.parse(url)
        // Check query parameters first (e.g. ?q=solo+leveling or ?title=...)
        val queryKeys = listOf("q", "query", "search", "title", "name", "film", "movie", "series", "file", "keyword")
        for (k in queryKeys) {
            val v = uri.getQueryParameter(k)
            if (!v.isNullOrBlank() && !isNoiseOrHashOrInvalidTitle(v)) {
                val cleaned = cleanMediaTitle(v)
                if (cleaned.length >= 2 && !isNoiseOrHashOrInvalidTitle(cleaned)) return cleaned
            }
        }

        // Check path segments
        val segments = uri.pathSegments
        if (!segments.isNullOrEmpty()) {
            val valid = segments.filter {
                it.length > 2 &&
                !it.matches(Regex("(?i)^(tt\\d+|\\d+|api|download|watch|tv|movie|stream|embed|play)$")) &&
                !isNoiseOrHashOrInvalidTitle(it)
            }
            if (valid.isNotEmpty()) {
                val cleaned = cleanMediaTitle(valid.last())
                if (cleaned.length >= 2 && !isNoiseOrHashOrInvalidTitle(cleaned)) return cleaned
            }
        }
    } catch (e: Exception) {}
    return ""
}

@Composable
fun DownloaderWebViewModal(
    title: String,
    searchQuery: String,
    initialUrl: String,
    videoDownloaderSiteUrl: String,
    movieDownloader02SiteUrl: String,
    vidsrcSiteUrl: String,
    directMediaDownloaderSiteUrl: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBackState by remember { mutableStateOf(false) }
    var hasSearchedInVideoDownloader by remember { mutableStateOf(false) }

    var editableTitle by remember { 
        mutableStateOf(if (title.isNotBlank() && title != "HD Search") title else searchQuery) 
    }
    var domExtractedTitle by remember { mutableStateOf("") }
    var domExtractedSeason by remember { mutableStateOf<Int?>(null) }
    var domExtractedEpisode by remember { mutableStateOf<Int?>(null) }

    val adBlockList = remember {
        listOf("adsterra", "popads", "bet365", "casino", "1xbet", "juicyads", "propellerads", "exoclick")
    }

    fun handleDownloadTrigger(urlStr: String, userAgent: String?, contentDisposition: String?, mimetype: String?) {
        try {
            val guessedName = URLUtil.guessFileName(urlStr, contentDisposition, mimetype)
            val extension = if (guessedName != null && guessedName.contains(".")) {
                val ext = guessedName.substringAfterLast(".", "mp4").lowercase()
                if (ext in listOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m3u8")) ext else "mp4"
            } else {
                "mp4"
            }

            val pageTitle = webViewRef?.title
            val cleanPageTitle = if (!pageTitle.isNullOrBlank() && !isNoiseOrHashOrInvalidTitle(pageTitle)) {
                cleanMediaTitle(pageTitle)
            } else ""

            val titleFromUrl = extractTitleFromUrl(urlStr)
            val titleFromPageUrl = extractTitleFromUrl(currentUrl)
            val titleFromGuessed = if (!guessedName.isNullOrBlank()) cleanMediaTitle(guessedName) else ""

            // Candidate titles in order of priority (domExtractedTitle & valid user editableTitle take top priority)
            val candidates = listOf(
                domExtractedTitle,
                if (!isNoiseOrHashOrInvalidTitle(editableTitle)) editableTitle else "",
                if (title.isNotBlank() && title != "HD Search" && !isNoiseOrHashOrInvalidTitle(title)) title else "",
                if (searchQuery.isNotBlank() && !isNoiseOrHashOrInvalidTitle(searchQuery)) searchQuery else "",
                titleFromPageUrl,
                cleanPageTitle,
                titleFromUrl,
                titleFromGuessed
            )

            var cleanName = ""
            for (candidate in candidates) {
                if (!candidate.isNullOrBlank() && !isNoiseOrHashOrInvalidTitle(candidate)) {
                    val cleaned = cleanMediaTitle(candidate)
                    if (cleaned.isNotBlank() && cleaned.length >= 2 && !isNoiseOrHashOrInvalidTitle(cleaned)) {
                        cleanName = cleaned
                        break
                    }
                }
            }

            if (cleanName.isBlank()) {
                cleanName = if (title.isNotBlank() && title != "HD Search" && !isNoiseOrHashOrInvalidTitle(title)) {
                    cleanMediaTitle(title)
                } else if (searchQuery.isNotBlank() && !isNoiseOrHashOrInvalidTitle(searchQuery)) {
                    cleanMediaTitle(searchQuery)
                } else {
                    "Media_Download"
                }
            }
            if (cleanName.isBlank() || isNoiseOrHashOrInvalidTitle(cleanName)) {
                cleanName = "Media_Download"
            }

            // Extract Season & Episode
            val (parsedSeason, parsedEpisode) = parseSeasonAndEpisodeFromInputs(
                editableTitle,
                domExtractedTitle,
                urlStr,
                currentUrl,
                guessedName,
                pageTitle,
                title,
                searchQuery
            )

            val finalSeason = parsedSeason ?: domExtractedSeason
            val finalEpisode = parsedEpisode ?: domExtractedEpisode

            val sanitizedBaseName = cleanName.replace(" ", "_").trim('_')

            val fileName = if (finalSeason != null && finalEpisode != null) {
                val sStr = String.format(java.util.Locale.US, "%02d", finalSeason)
                val eStr = String.format(java.util.Locale.US, "%02d", finalEpisode)
                "${sanitizedBaseName}_S${sStr}E${eStr}.$extension"
            } else if (finalSeason != null) {
                val sStr = String.format(java.util.Locale.US, "%02d", finalSeason)
                "${sanitizedBaseName}_S${sStr}.$extension"
            } else if (finalEpisode != null) {
                val eStr = String.format(java.util.Locale.US, "%02d", finalEpisode)
                "${sanitizedBaseName}_EP${eStr}.$extension"
            } else {
                "${sanitizedBaseName}.$extension"
            }

            val cookies = try {
                android.webkit.CookieManager.getInstance().getCookie(urlStr)
            } catch (e: Exception) {
                null
            }
            
            MediaDownloader.downloadFile(
                context = context,
                url = urlStr,
                fileName = fileName,
                coroutineScope = kotlinx.coroutines.MainScope(),
                userAgent = userAgent,
                cookies = cookies,
                referer = currentUrl
            )
            Toast.makeText(context, "Download Started: $fileName\nCheck notification bar for progress.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download trigger error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = true) {
            if (webViewRef?.canGoBack() == true) {
                webViewRef?.goBack()
            } else {
                onClose()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SpaceBlack
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar with Title Search Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepSlate)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (canGoBackState) {
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Go Back",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Downloader",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Editable Title / Search Bar
                    OutlinedTextField(
                        value = editableTitle,
                        onValueChange = { editableTitle = it },
                        placeholder = {
                            Text(
                                text = "Media Name...",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (editableTitle.isNotBlank()) {
                                        val escaped = editableTitle.replace("\"", "\\\"").replace("'", "\\'")
                                        val searchJs = "javascript:(function(){var inps=document.querySelectorAll('input[type=\"text\"],input[type=\"search\"],input[name=\"q\"],input[name=\"query\"],input[name=\"search\"]');for(var i=0;i<inps.length;i++){inps[i].value='" + escaped + "';inps[i].dispatchEvent(new Event('input',{bubbles:true}));inps[i].dispatchEvent(new Event('change',{bubbles:true}));var f=inps[i].form;if(f){f.submit();return;}}var btn=document.querySelector('button[type=\"submit\"],input[type=\"submit\"],.search-btn,#search-btn');if(btn)btn.click();})();"
                                        webViewRef?.loadUrl(searchJs)
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search In Page",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = SpaceBlack.copy(alpha = 0.5f),
                            unfocusedContainerColor = SpaceBlack.copy(alpha = 0.5f),
                            cursorColor = NeonCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    )

                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonCyan,
                        trackColor = DeepSlate
                    )
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                keepScreenOn = true
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mediaPlaybackRequiresUserGesture = false
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }

                                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                                addJavascriptInterface(object {
                                    @android.webkit.JavascriptInterface
                                    fun updateMedia(t: String?, s: Int, e: Int) {
                                        mainHandler.post {
                                            if (!t.isNullOrBlank()) {
                                                val tClean = cleanMediaTitle(t)
                                                if (tClean.isNotBlank() && !isNoiseOrHashOrInvalidTitle(tClean) && tClean.length >= 2) {
                                                    domExtractedTitle = tClean
                                                    val seasonToUse = if (s > 0) s else (domExtractedSeason ?: 1)
                                                    val episodeToUse = if (e > 0) e else (domExtractedEpisode ?: 1)
                                                    val seasonEpSuffix = if (s > 0 || e > 0 || domExtractedEpisode != null) {
                                                        " S" + String.format(java.util.Locale.US, "%02d", seasonToUse) + "E" + String.format(java.util.Locale.US, "%02d", episodeToUse)
                                                    } else ""
                                                    
                                                    if (editableTitle.isBlank() || editableTitle == "HD Search" || isNoiseOrHashOrInvalidTitle(editableTitle)) {
                                                        editableTitle = tClean + seasonEpSuffix
                                                    }
                                                }
                                            }
                                            if (s > 0) domExtractedSeason = s
                                            if (e > 0) domExtractedEpisode = e
                                        }
                                    }
                                }, "AndroidMediaExtractor")

                                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                    handleDownloadTrigger(url, userAgent, contentDisposition, mimetype)
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: android.os.Message?
                                    ): Boolean {
                                        val newWebView = WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.userAgentString = view?.settings?.userAgentString ?: "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                                handleDownloadTrigger(url, userAgent, contentDisposition, mimetype)
                                            }
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                                    val uStr = req?.url?.toString() ?: return false
                                                    val uLower = uStr.lowercase()
                                                    if (adBlockList.any { uLower.contains(it) }) return true
                                                    if (uLower.endsWith(".mp4") || uLower.endsWith(".m3u8") ||
                                                        uLower.contains("get_download") || uLower.contains("download_file") ||
                                                        (uLower.contains("/api/download/") && !uLower.contains("02moviedownloader.site")) || uLower.contains("/download") ||
                                                        uLower.contains(".ts") || uLower.contains("dl?")
                                                    ) {
                                                        handleDownloadTrigger(uStr, v?.settings?.userAgentString, null, "video/mp4")
                                                        return true
                                                    }
                                                    v?.loadUrl(uStr)
                                                    return true
                                                }
                                            }
                                        }
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        transport?.webView = newWebView
                                        resultMsg?.sendToTarget()
                                        return true
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        if (!url.isNullOrEmpty()) currentUrl = url
                                        canGoBackState = view?.canGoBack() == true

                                        // Inject robust media detector script
                                        val detectorJs = """
                                            javascript:(function(){
                                                function isNoise(s){
                                                    if(!s||s.length<2)return true;
                                                    var l=s.toLowerCase().trim();
                                                    if(/^[0-9a-f]{8,}$/i.test(l))return true;
                                                    if(/[0-9a-f]{12,}/i.test(l))return true;
                                                    if(/^[0-9a-zA-Z_-]{16,}$/.test(l)&&l.indexOf(' ')===-1)return true;
                                                    var noise=[
                                                        'omnisandbox','videodownloader','02moviedownloader','vidsrc','free online hd',
                                                        'hd search','fast in-app','direct media','watch online free','homeairtv',
                                                        'download video','video downloader','movie downloader','from youtube',
                                                        'youtube','instagram','tiktok','facebook','twitter','vimeo','dailymotion',
                                                        'from youtube instagram more','youtube instagram more','download options',
                                                        'select episode','select quality','bulk select','download videos','download from',
                                                        'video er hd mp4','free online hd mp4 downloads','media_download','video player',
                                                        'player','stream','download','downloads','direct download','fast download'
                                                    ];
                                                    for(var i=0;i<noise.length;i++){if(l.indexOf(noise[i])!==-1&&s.length<80)return true;}
                                                    return false;
                                                }

                                                function detect(){
                                                    var foundTitle='';
                                                    var season=1;
                                                    var episode=1;

                                                    // 1. Direct card & media headings
                                                    var sels=[
                                                        '.card-title','.movie-title','.film-name','.film-detail h1','.film-detail h2','.film-detail h3',
                                                        '.media-card h1','.media-card h2','.media-card h3','.detail h1','.detail h2','.detail h3',
                                                        '.movie-info h1','.movie-info h2','.anime-title','h1','h2','h3','h4','[itemprop="name"]'
                                                    ];
                                                    for(var s=0;s<sels.length;s++){
                                                        var els=document.querySelectorAll(sels[s]);
                                                        for(var j=0;j<els.length;j++){
                                                            var t=(els[j].innerText||els[j].textContent||'').trim();
                                                            if(t&&t.length>=2&&!isNoise(t)){
                                                                foundTitle=t;
                                                                break;
                                                            }
                                                        }
                                                        if(foundTitle)break;
                                                    }

                                                    // 2. Headings adjacent to poster images
                                                    if(!foundTitle){
                                                        var imgs=document.querySelectorAll('img');
                                                        for(var m=0;m<imgs.length;m++){
                                                            var img=imgs[m];
                                                            if(img.offsetWidth>50||img.offsetHeight>50||(img.src&&(img.src.includes('poster')||img.src.includes('cover')))){
                                                                var p=img.closest('.card, .media-card, .film-detail, .movie-info, div');
                                                                if(p){
                                                                    var h=p.querySelector('h1,h2,h3,h4,strong,b,.title,.name');
                                                                    if(h){
                                                                        var ht=(h.innerText||h.textContent||'').trim();
                                                                        if(ht&&ht.length>=2&&!isNoise(ht)){
                                                                            foundTitle=ht;
                                                                            break;
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 3. Search inputs
                                                    if(!foundTitle){
                                                        var inps=document.querySelectorAll('input[type="text"],input[type="search"],input[name="q"],input[name="query"],input[name="search"],input[name="keyword"],#search,.search-input');
                                                        for(var i=0;i<inps.length;i++){
                                                            var v=(inps[i].value||'').trim();
                                                            if(v&&v.length>=2&&!isNoise(v)){foundTitle=v;break;}
                                                        }
                                                    }

                                                    // 4. OpenGraph & Meta
                                                    if(!foundTitle){
                                                        var og=document.querySelector('meta[property="og:title"],meta[name="twitter:title"]');
                                                        if(og&&og.content&&!isNoise(og.content)){foundTitle=og.content.trim();}
                                                    }

                                                    // Extract Season
                                                    var seasonSelect=document.querySelector('select');
                                                    if(seasonSelect){
                                                        var optText=seasonSelect.options[seasonSelect.selectedIndex]?seasonSelect.options[seasonSelect.selectedIndex].text:'';
                                                        var sM=optText.match(/[sS]eason\s*(\d+)/i)||(seasonSelect.value||'').match(/(\d+)/);
                                                        if(sM)season=parseInt(sM[1],10);
                                                    }else{
                                                        var bTxt=document.body?document.body.innerText:'';
                                                        var sM2=bTxt.match(/[sS]eason\s*(\d+)/i)||(window.location.href||'').match(/\/tv\/[^\/]+\/(\d+)/);
                                                        if(sM2)season=parseInt(sM2[1],10);
                                                    }

                                                    // Extract Episode (active/selected button or badge)
                                                    var epBtns=document.querySelectorAll('button, .btn, .episode-btn, .ep-btn, a, div[role="button"], span');
                                                    var foundEp=0;
                                                    for(var b=0;b<epBtns.length;b++){
                                                        var btn=epBtns[b];
                                                        var btnTxt=(btn.innerText||btn.textContent||'').trim();
                                                        if(/^\d{1,4}$/.test(btnTxt)){
                                                            var num=parseInt(btnTxt,10);
                                                            var cls=(btn.className||'').toLowerCase();
                                                            var isSel=cls.indexOf('active')!==-1||cls.indexOf('selected')!==-1||cls.indexOf('success')!==-1||cls.indexOf('primary')!==-1||cls.indexOf('green')!==-1;
                                                            if(isSel){foundEp=num;break;}
                                                        }
                                                    }
                                                    if(foundEp>0){
                                                        episode=foundEp;
                                                    }else{
                                                        var full=(document.title||'')+' '+foundTitle+' '+(window.location.href||'');
                                                        var sm=full.match(/[sS](\d+)[eE](\d+)/)||full.match(/[sS]eason\s*(\d+)\s*[eE]pisode\s*(\d+)/i)||full.match(/[sS](\d+)\s*[eE][pP]?\s*(\d+)/i);
                                                        if(sm){season=parseInt(sm[1],10);episode=parseInt(sm[2],10);}
                                                    }

                                                    if(foundTitle&&foundTitle.length>=2&&window.AndroidMediaExtractor){
                                                        window.AndroidMediaExtractor.updateMedia(foundTitle,season,episode);
                                                    }
                                                }

                                                detect();
                                                setInterval(detect,800);
                                                document.addEventListener('input',detect,true);
                                                document.addEventListener('change',detect,true);
                                                document.addEventListener('click',function(e){
                                                    var target=e.target;
                                                    if(target){
                                                        var txt=(target.innerText||target.textContent||'').trim();
                                                        if(/^\d{1,4}$/.test(txt)){
                                                            var epNum=parseInt(txt,10);
                                                            if(epNum>0&&window.AndroidMediaExtractor){
                                                                window.AndroidMediaExtractor.updateMedia('',0,epNum);
                                                            }
                                                        }
                                                    }
                                                    setTimeout(detect,250);
                                                    setTimeout(detect,1000);
                                                },true);
                                                if(window.MutationObserver){
                                                    var obs=new MutationObserver(function(){detect();});
                                                    obs.observe(document.documentElement,{childList:true,subtree:true});
                                                }
                                            })();
                                        """.trimIndent().replace("\n", "")
                                        view?.loadUrl(detectorJs)

                                        if (!hasSearchedInVideoDownloader && url != null && url.trimEnd('/') == videoDownloaderSiteUrl.trimEnd('/')) {
                                            if (searchQuery.isNotBlank()) {
                                                hasSearchedInVideoDownloader = true
                                                val escapedQuery = searchQuery.replace("\"", "\\\"").replace("'", "\\'")
                                                val searchInitJs = "javascript:(function(){var query='" + escapedQuery + "';function doSearch(){var inputs=document.querySelectorAll('input[type=\"text\"],input[type=\"search\"],input[type=\"url\"],input:not([type])');var foundInput=null;for(var i=0;i<inputs.length;i++){var inp=inputs[i];if(inp.offsetWidth>0||inp.offsetHeight>0){foundInput=inp;break;}}if(foundInput){foundInput.focus();foundInput.value=query;foundInput.dispatchEvent(new Event('input',{bubbles:true}));foundInput.dispatchEvent(new Event('change',{bubbles:true}));var forms=document.querySelectorAll('form');for(var j=0;j<forms.length;j++){if(forms[j].contains(foundInput)){forms[j].submit();return;}}var buttons=document.querySelectorAll('button,input[type=\"submit\"],a.btn,div.btn');for(var k=0;k<buttons.length;k++){var btnText=(buttons[k].innerText||buttons[k].value||'').toLowerCase();if(btnText.includes('search')||btnText.includes('download')||btnText.includes('go')||btnText.includes('fetch')){buttons[k].click();return;}}}}setTimeout(doSearch,800);setTimeout(doSearch,2000);})();"
                                                view?.loadUrl(searchInitJs)
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val urlLower = url.lowercase()

                                        if (adBlockList.any { urlLower.contains(it) }) return true

                                        if (urlLower.endsWith(".mp4") || urlLower.endsWith(".m3u8") ||
                                            urlLower.contains("get_download") || urlLower.contains("download_file") ||
                                            (urlLower.contains("/api/download/") && !urlLower.contains("02moviedownloader.site")) || urlLower.contains("/download") ||
                                            urlLower.contains(".ts") || urlLower.contains("dl?")
                                        ) {
                                            handleDownloadTrigger(url, view?.settings?.userAgentString, null, "video/mp4")
                                            return true
                                        }

                                        return false
                                    }
                                }

                                loadUrl(currentUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
