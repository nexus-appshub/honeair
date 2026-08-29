package com.example.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var showStoreWebView by remember { mutableStateOf(false) }

    val currentVersionName = remember { AppUpdateManager.getAppVersionName(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, downloadedFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val file = downloadedFile
                if (file != null && file.exists()) {
                    if (AppUpdateManager.canInstallUnknownPackages(context)) {
                        AppUpdateManager.installApk(context, file)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate && !isDownloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate && !isDownloading,
            dismissOnClickOutside = !updateInfo.forceUpdate && !isDownloading,
            usePlatformDefaultWidth = false // Required for full-screen
        )
    ) {
        // Prevent hardware/gesture back key on android completely when force update is active
        BackHandler(enabled = updateInfo.forceUpdate || isDownloading) {
            // No-op: consumes the back event so user is locked into updating
        }

        // Full screen for Force Update, otherwise a floating popup
        Surface(
            shape = if (updateInfo.forceUpdate) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
            color = DeepSlate,
            border = if (updateInfo.forceUpdate) null else BorderStroke(1.dp, Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta))),
            modifier = if (updateInfo.forceUpdate) {
                Modifier.fillMaxSize() // Full-screen block
            } else {
                Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 680.dp)
                    .wrapContentHeight()
                    .padding(12.dp)
            },
            shadowElevation = if (updateInfo.forceUpdate) 0.dp else 24.dp
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(if (updateInfo.forceUpdate) 32.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (updateInfo.forceUpdate) Arrangement.Center else Arrangement.Top
            ) {
                // Top Icon Badge with Animated Gradient Glow
                Box(
                    modifier = Modifier
                        .size(if (updateInfo.forceUpdate) 80.dp else 64.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            CircleShape
                        )
                        .background(SpaceBlack, CircleShape)
                        .border(1.5.dp, NeonCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "App Update",
                        tint = NeonCyan,
                        modifier = Modifier.size(if (updateInfo.forceUpdate) 40.dp else 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title Banner
                Text(
                    text = if (updateInfo.forceUpdate) "Update Required" else "New Update Available!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (updateInfo.forceUpdate) FlameGold else TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                if (updateInfo.forceUpdate) {
                    Text(
                        text = "Your current version is outdated and no longer supported. Please update to the latest version to continue using the app.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Version Tag Chips
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(
                            text = "v$currentVersionName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Update to",
                        tint = NeonCyan,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(16.dp)
                    )

                    Surface(
                        color = NeonCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonCyan)
                    ) {
                        Text(
                            text = "v${updateInfo.latestVersionName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Release Notes Card (only show if not force update or if release notes exist)
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpaceBlack, RoundedCornerShape(16.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = FlameGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "What's New in this Version:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val notesScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .heightIn(max = if (updateInfo.forceUpdate) 200.dp else 130.dp)
                                .verticalScroll(notesScrollState)
                        ) {
                            updateInfo.releaseNotes.lines().forEach { line ->
                                if (line.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = if (line.trim().startsWith("•") || line.trim().startsWith("-")) "" else "• ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = line.trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Download Progress / Installation Status Section
                if (isDownloading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (downloadProgress >= 100) "Download Complete! Preparing Install..." else "Downloading Update APK...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$downloadProgress%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = NeonCyan,
                            trackColor = SpaceBlack
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (downloadError != null) {
                    Text(
                        text = downloadError ?: "",
                        fontSize = 13.sp,
                        color = Color(0xFFFF5252),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Ready to Install Trigger Button
                if (downloadedFile != null && !isDownloading) {
                    Button(
                        onClick = {
                            val installed = AppUpdateManager.installApk(context, downloadedFile!!)
                            if (!installed) {
                                Toast.makeText(context, "Please allow 'Install Unknown Apps' permission.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853))
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = "Install APK",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Install APK Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Main Action Buttons
                if (!isDownloading && downloadedFile == null) {
                    // Update Now (In-App Direct Download)
                    Button(
                        onClick = {
                            isDownloading = true
                            downloadError = null

                            val appContext = context.applicationContext
                            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                val apkFile = AppUpdateManager.downloadApk(
                                    context = appContext,
                                    downloadUrl = updateInfo.apkDownloadUrl,
                                    onProgress = { progress -> downloadProgress = progress }
                                )
                                isDownloading = false
                                if (apkFile != null && apkFile.exists()) {
                                    if (AppUpdateManager.isValidApkFile(apkFile)) {
                                        downloadedFile = apkFile
                                    } else {
                                        apkFile.delete()
                                        downloadError = "Invalid installer file. Please open Store instead."
                                    }
                                } else {
                                    downloadError = "Failed to download update. Opening store..."
                                    showStoreWebView = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Update Now",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update Now",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Open AppsHub Store Webview Option
                    OutlinedButton(
                        onClick = { showStoreWebView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "AppsHub Store",
                            tint = NeonMagenta,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update in AppsHub",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }
                }

                // Update Later dismiss button
                if (!isDownloading && !updateInfo.forceUpdate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Update Later",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // In-App WebView Store Modal
    if (showStoreWebView) {
        AppsHubWebViewModal(
            url = updateInfo.storePageUrl,
            onClose = { showStoreWebView = false }
        )
    }
}

/**
 * Fullscreen In-App WebView for AppsHub Store page browsing & manual download.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppsHubWebViewModal(
    url: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SpaceBlack
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // WebView Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepSlate)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = "Store",
                            tint = NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "App Store",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = url,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "External Browser",
                                tint = TextSecondary
                            )
                        }

                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextPrimary
                            )
                        }
                    }
                }

                // WebView Core
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = "Mozilla/5.0 (Linux; Android 12) WebView/1.0"
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url != null && url.endsWith(".apk", ignoreCase = true)) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        ctx.startActivity(intent)
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

