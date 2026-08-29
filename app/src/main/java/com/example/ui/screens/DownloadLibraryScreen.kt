package com.example.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.StreamViewModel
import com.example.download.MediaDownloader
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadLibraryScreen(
    viewModel: StreamViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeDownloads by MediaDownloader.activeDownloads.collectAsState()
    var downloadedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var activePlayFile by remember { mutableStateOf<File?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    var showManualDownloadDialog by remember { mutableStateOf(false) }
    var showHdSearchWebView by remember { mutableStateOf(false) }
    var manualSearchText by remember { mutableStateOf("") }

    // Load files
    fun refreshFiles() {
        val allFiles = getDownloadedFiles(context)
        val activeNames = activeDownloads.map { it.title }
        downloadedFiles = allFiles.filter { it.name !in activeNames }
    }

    LaunchedEffect(Unit, activeDownloads) {
        refreshFiles()
    }

    if (showHdSearchWebView) {
        com.example.ui.components.DownloaderWebViewModal(
            title = if (manualSearchText.isNotBlank()) manualSearchText.trim() else "HD Search",
            searchQuery = manualSearchText.trim(),
            initialUrl = "https://videodownloader.site/",
            videoDownloaderSiteUrl = "https://videodownloader.site/",
            movieDownloader02SiteUrl = "https://02moviedownloader.site/",
            vidsrcSiteUrl = "https://vidsrc.me/",
            directMediaDownloaderSiteUrl = "https://vidsrc.net/",
            onClose = { showHdSearchWebView = false }
        )
    }

    if (showManualDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showManualDownloadDialog = false },
            containerColor = DeepSlate,
            title = {
                Text(
                    text = "Manual Download Options",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Enter movie/series name to download or search directly on web downloader:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    OutlinedTextField(
                        value = manualSearchText,
                        onValueChange = { manualSearchText = it },
                        placeholder = { 
                            Text(
                                text = "e.g. Solo Leveling S01E01",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            ) 
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeonCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular Option 1: HD Search
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showManualDownloadDialog = false
                                    showHdSearchWebView = true
                                }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                    .border(2.dp, NeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "HD Search",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "HD Search",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Circular Option 2: Browse
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showManualDownloadDialog = false
                                    viewModel.setSelectedTabIndex(1) // Select Browse Tab
                                    onBack()
                                }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Browse",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Browse",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManualDownloadDialog = false }) {
                    Text("Close", color = NeonCyan)
                }
            }
        )
    }

    LaunchedEffect(activePlayFile) {
        viewModel.setFullScreen(activePlayFile != null)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setFullScreen(false)
        }
    }

    if (activePlayFile != null) {
        OfflineVideoPlayerScreen(
            videoFile = activePlayFile!!,
            onBack = { activePlayFile = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "My Downloads",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary
                        )
                    },
                    actions = {
                        Button(
                            onClick = { showManualDownloadDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Manual Download",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SpaceBlack
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            },
            containerColor = SpaceBlack,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(SpaceBlack)
            ) {
                if (downloadedFiles.isEmpty() && activeDownloads.isEmpty()) {
                    // Beautiful Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = "No Downloads Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Videos you download will appear here so you can watch them offline anytime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Find Videos to Download",
                                fontWeight = FontWeight.Bold,
                                color = SpaceBlack
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (activeDownloads.isNotEmpty()) {
                            item {
                                Text("Downloading...", fontWeight = FontWeight.Bold, color = NeonCyan, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                            }
                            items(activeDownloads) { active ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = DeepSlate),
                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { active.progress / 100f },
                                                color = NeonCyan,
                                                strokeWidth = 3.dp,
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Text("${active.progress}%", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = active.title,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (active.isPaused) "Paused • ${active.getFormattedStatus()}" else "Downloading: ${active.getFormattedStatus()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Pause / Resume Button
                                            IconButton(
                                                onClick = {
                                                    MediaDownloader.togglePauseDownload(context, active.id)
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (active.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                    contentDescription = if (active.isPaused) "Resume Download" else "Pause Download",
                                                    tint = if (active.isPaused) Color(0xFFFFB74D) else NeonCyan,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Cancel Button
                                            IconButton(
                                                onClick = {
                                                    MediaDownloader.cancelDownload(active.id)
                                                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Cancel Download",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (downloadedFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Completed",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32), // Deep Green
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                                )
                            }
                            items(downloadedFiles) { file ->
                                DownloadItemCard(
                                    file = file,
                                    onPlay = { activePlayFile = file },
                                    onDelete = { fileToDelete = file }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modern Confirm Delete Dialog
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = {
                Text(
                    text = "Delete Download?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete '${fileToDelete!!.nameWithoutExtension.replace("_", " ")}' from device storage?",
                    color = TextSecondary
                )
            },
            containerColor = DeepSlate,
            confirmButton = {
                Button(
                    onClick = {
                        val file = fileToDelete
                        if (file != null) {
                            var deleted = false
                            try {
                                if (file.exists()) {
                                    // Instantly truncate file to 0 bytes to reclaim device storage space permanently
                                    try {
                                        java.io.FileOutputStream(file).use { fos ->
                                            fos.getChannel().truncate(0)
                                        }
                                    } catch (_: Exception) {}
                                    deleted = file.delete()
                                }
                            } catch (_: Exception) {}

                            // 1. Remove from Android MediaStore database (Video & Downloads)
                            try {
                                val videoUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                context.contentResolver.delete(
                                    videoUri,
                                    android.provider.MediaStore.Video.Media.DATA + "=?",
                                    arrayOf(file.absolutePath)
                                )
                            } catch (_: Exception) {}

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                try {
                                    val downloadUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                                    context.contentResolver.delete(
                                        downloadUri,
                                        android.provider.MediaStore.MediaColumns.DATA + "=?",
                                        arrayOf(file.absolutePath)
                                    )
                                } catch (_: Exception) {}
                            }

                            // 2. Notify MediaScanner to refresh OS index immediately
                            try {
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(file.absolutePath),
                                    null
                                ) { _, _ -> }
                            } catch (_: Exception) {}

                            // 3. Clean up video progress tracking SharedPreferences keys
                            try {
                                val prefs = context.getSharedPreferences("video_progress", Context.MODE_PRIVATE)
                                prefs.edit()
                                    .remove(file.absolutePath)
                                    .remove("${file.absolutePath}_duration")
                                    .apply()
                            } catch (_: Exception) {}

                            Toast.makeText(context, "Permanently deleted from device storage", Toast.LENGTH_SHORT).show()
                            refreshFiles()
                        }
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { fileToDelete = null },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DownloadItemCard(
    file: File,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cleanName = remember(file.name) {
        var name = file.nameWithoutExtension
            .replace("_", " ")
            .replace("-", " ")
            .replace("hls", "", ignoreCase = true)
            .trim()
        if (name.matches(Regex("(?i)^[0-9a-f]{12,}.*"))) {
            name = name.replace(Regex("(?i)^[0-9a-f]{12,}\\s*"), "Media ")
        }
        name.trim()
    }
    
    val fileSize = remember(file.length()) { formatSize(file.length()) }
    val fileDate = remember(file.lastModified()) { formatDate(file.lastModified()) }

    // Fetch saved playback progress
    val progressPercent = remember(file.absolutePath) {
        val prefs = context.getSharedPreferences("video_progress", Context.MODE_PRIVATE)
        val pos = prefs.getLong(file.absolutePath, 0L)
        val dur = prefs.getLong("${file.absolutePath}_duration", 0L)
        if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
    }

    // Retrieve video thumbnail asynchronously with multi-offset probing and stylized fallback
    var thumbnailBitmap by remember(file.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.absolutePath, cleanName) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bmp = extractSmartVideoThumbnail(file, cleanName)
            thumbnailBitmap = bmp
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Indicator Left Icon (Video Preview Thumbnail Card)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Video Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    // Fallback background icon
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (progressPercent in 0.01f..0.98f) NeonCyan.copy(alpha = 0.6f) else TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Circular Progress Bar Overlay for Watched Progress
                if (progressPercent > 0.01f) {
                    if (progressPercent >= 0.98f) {
                        // Fully Watched Checkmark Overlay
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color(0xFF00E676), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Watched",
                                tint = SpaceBlack,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    } else {
                        // Dynamic Circular Progress Ring around the thumbnail
                        CircularProgressIndicator(
                            progress = { progressPercent },
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            color = NeonCyan,
                            strokeWidth = 3.dp,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Meta Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = cleanName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val progressText = if (progressPercent in 0.01f..0.98f) {
                    "  •  ${(progressPercent * 100).toInt()}% watched"
                } else if (progressPercent >= 0.98f) {
                    "  •  Watched"
                } else {
                    ""
                }
                
                Text(
                    text = "$fileSize$progressText  •  $fileDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progressPercent in 0.01f..0.98f) NeonCyan else TextSecondary,
                    maxLines = 1
                )
            }

            // Play & Delete Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Download",
                        tint = NeonMagenta,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getDownloadedFiles(context: Context): List<File> {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    if (dir == null || !dir.exists()) return emptyList()
    return dir.listFiles()?.filter {
        it.isFile && (
            it.name.endsWith(".mp4", true) || 
            it.name.endsWith(".mkv", true) || 
            it.name.endsWith(".webm", true) || 
            it.name.endsWith(".ts", true)
        )
    }?.sortedByDescending { it.lastModified() } ?: emptyList()
}

private fun formatSize(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format(Locale.getDefault(), "%.2f GB", mb / 1024)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun extractSmartVideoThumbnail(file: File, displayName: String): android.graphics.Bitmap {
    try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)

        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: 0L
        val durationUs = durationMs * 1000L

        val timeOffsetsUs = if (durationUs > 30_000_000L) {
            listOf(
                (durationUs * 0.15).toLong(),
                (durationUs * 0.25).toLong(),
                (durationUs * 0.40).toLong(),
                15_000_000L,
                30_000_000L,
                5_000_000L,
                1_000_000L
            )
        } else if (durationUs > 5_000_000L) {
            listOf(
                (durationUs * 0.20).toLong(),
                (durationUs * 0.50).toLong(),
                3_000_000L,
                1_000_000L
            )
        } else {
            listOf(1_000_000L, 500_000L, 0L)
        }

        var candidateFrame: android.graphics.Bitmap? = null
        for (timeUs in timeOffsetsUs) {
            val frame = try {
                retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (e: Exception) {
                null
            }

            if (frame != null) {
                if (!isBitmapMostlyBlack(frame)) {
                    val scaled = android.graphics.Bitmap.createScaledBitmap(frame, 160, 160, true)
                    retriever.release()
                    return scaled
                } else if (candidateFrame == null) {
                    candidateFrame = frame
                }
            }
        }
        retriever.release()

        if (candidateFrame != null && !isBitmapMostlyBlack(candidateFrame)) {
            return android.graphics.Bitmap.createScaledBitmap(candidateFrame, 160, 160, true)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Fallback: Generate an eye-catching gradient poster badge
    return generateStylizedPoster(displayName)
}

private fun isBitmapMostlyBlack(bitmap: android.graphics.Bitmap): Boolean {
    val sampleW = minOf(24, bitmap.width)
    val sampleH = minOf(24, bitmap.height)
    if (sampleW <= 0 || sampleH <= 0) return true
    val small = android.graphics.Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
    var totalLuminance = 0L
    for (x in 0 until sampleW) {
        for (y in 0 until sampleH) {
            val pixel = small.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalLuminance += (r * 299 + g * 587 + b * 114) / 1000
        }
    }
    small.recycle()
    val avgLuminance = totalLuminance / (sampleW * sampleH)
    return avgLuminance < 22 // Too dark / pure black frame
}

private fun generateStylizedPoster(title: String): android.graphics.Bitmap {
    val size = 160
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val hash = kotlin.math.abs(title.hashCode())
    val palettes = listOf(
        Pair(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        Pair(0xFF1F1C2C.toInt(), 0xFF928DAB.toInt()),
        Pair(0xFF2C3E50.toInt(), 0xFF3498DB.toInt()),
        Pair(0xFF141E30.toInt(), 0xFF243B55.toInt()),
        Pair(0xFF000428.toInt(), 0xFF004E92.toInt()),
        Pair(0xFF1A2980.toInt(), 0xFF26D0CE.toInt()),
        Pair(0xFF3A1C71.toInt(), 0xFFD76D77.toInt()),
        Pair(0xFF1D2671.toInt(), 0xFFC33764.toInt())
    )
    val palette = palettes[hash % palettes.size]

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, size.toFloat(), size.toFloat(),
        palette.first, palette.second,
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.shader = null

    // Draw initials
    val words = title.split(" ").filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    val initials = words.take(2).map { it.first().uppercaseChar() }.joinToString("").ifEmpty { "HD" }

    paint.color = android.graphics.Color.WHITE
    paint.textSize = 46f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.textAlign = android.graphics.Paint.Align.CENTER
    val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
    canvas.drawText(initials, size / 2f, yPos, paint)

    return bitmap
}
