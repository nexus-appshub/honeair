package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

data class DownloadInfo(
    val id: String,
    val title: String,
    val progress: Int,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    var isCancelled: Boolean = false,
    val isPaused: Boolean = false,
    val job: Job? = null
) {
    fun getFormattedStatus(): String {
        val downloadedStr = formatByteSize(downloadedBytes)
        if (totalBytes > 0L) {
            val totalStr = formatByteSize(totalBytes)
            return "$downloadedStr / $totalStr ($progress%)"
        }
        return "$downloadedStr ($progress%)"
    }
}

object MediaDownloader {
    data class HlsQuality(
        val url: String,
        val resolution: String,
        val bandwidth: Long = 0L
    )

    private const val TAG = "MediaDownloader"
    private const val CHANNEL_ID = "media_downloads_channel"
    private const val CHANNEL_NAME = "Media Downloads"
    private const val NOTIFICATION_ID_BASE = 1000

    private val _activeDownloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadInfo>> = _activeDownloads

    private val builders = ConcurrentHashMap<String, NotificationCompat.Builder>()
    private var isReceiverRegistered = false

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val downloadId = intent.getStringExtra("download_id") ?: return
            when (action) {
                "com.example.download.ACTION_CANCEL" -> {
                    cancelDownload(downloadId)
                }
                "com.example.download.ACTION_TOGGLE_PAUSE" -> {
                    togglePauseDownload(context, downloadId)
                }
            }
        }
    }

    private fun registerReceiverIfNeeded(context: Context) {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction("com.example.download.ACTION_CANCEL")
                addAction("com.example.download.ACTION_TOGGLE_PAUSE")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext,
                downloadReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    fun resolveDefaultReferer(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("megaplay") || lower.contains("kryntal") -> "https://megaplay.buzz/"
            lower.contains("anikoto") -> "https://anikoto.cz/"
            lower.contains("dokicloud") -> "https://dokicloud.one/"
            lower.contains("rabbitstream") -> "https://rabbitstream.net/"
            lower.contains("megacloud") -> "https://megacloud.tv/"
            lower.contains("animanga") -> "https://tiktoks.animanga.fun/"
            lower.contains("vidnest") -> "https://vidnest.fun/"
            lower.contains("rogflix") -> "https://rogflix.fun/"
            lower.contains("vidrock") -> "https://vidrock.net/"
            lower.contains("vidlink") -> "https://vidlink.pro/"
            lower.contains("02movie") -> "https://02moviedownloader.site/"
            lower.contains("vidsrc") -> "https://vidsrc.me/"
            else -> {
                val host = try { Uri.parse(url).host } catch (e: Exception) { null }
                if (!host.isNullOrBlank()) "https://$host/" else "https://anikoto.cz/"
            }
        }
    }

    fun fetchStreamContent(
        url: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ): String {
        val host = try { Uri.parse(url).host } catch (e: Exception) { null } ?: ""
        val candidateRequests = mutableListOf<Request>()

        // 1. Direct provided / auto-detected referer request
        val effectiveReferer = if (!referer.isNullOrBlank()) referer else resolveDefaultReferer(url)
        candidateRequests.add(getRequest(url, userAgent, cookies, effectiveReferer))

        // 2. Anikoto / Megaplay specific referers if applicable
        val isAnikotoOrMegaplay = url.contains("megaplay") || url.contains("kryntal") || url.contains("anikoto") ||
                effectiveReferer.contains("anikoto") || effectiveReferer.contains("megaplay") || effectiveReferer.contains("kryntal")
        if (isAnikotoOrMegaplay) {
            for (ref in listOf("https://megaplay.buzz/", "https://anikoto.cz/", "https://kryntal.top/", "https://dokicloud.one/")) {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", ref)
                    .header("Origin", ref.removeSuffix("/"))
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "cross-site")
                    .build()
                candidateRequests.add(req)
            }
        }

        // 3. Host-derived Referer & Origin with Chrome UA
        if (host.isNotEmpty()) {
            val cookieVal = cookies ?: try {
                android.webkit.CookieManager.getInstance().getCookie(url)
            } catch (e: Exception) { null }

            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://$host/")
                .header("Origin", "https://$host")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
            if (!cookieVal.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookieVal)
            }
            candidateRequests.add(reqBuilder.build())
        }

        // 4. Known Stream Host Referers
        val commonReferers = listOf(
            "https://anikoto.cz/",
            "https://megaplay.buzz/",
            "https://kryntal.top/",
            "https://vidnest.fun/",
            "https://vidrock.net/",
            "https://vidsrc.me/",
            "https://autoembed.cc/",
            "https://filxer.com/",
            "https://2embed.cc/",
            "https://02moviedownloader.site/"
        )
        for (ref in commonReferers) {
            candidateRequests.add(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Referer", ref)
                    .header("Origin", ref.removeSuffix("/"))
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "cross-site")
                    .build()
            )
        }

        // 5. Android ExoPlayer User-Agent without Referer
        candidateRequests.add(
            Request.Builder()
                .url(url)
                .header("User-Agent", "ExoPlayerLib/2.19.1 (Linux; Android 14)")
                .header("Accept", "*/*")
                .build()
        )

        var lastCode = 0
        var lastErr: Exception? = null
        for (req in candidateRequests) {
            try {
                val resp = okHttpClient.newCall(req).execute()
                lastCode = resp.code
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    resp.close()
                    if (!body.isNullOrBlank()) {
                        return body
                    }
                }
                resp.close()
            } catch (e: Exception) {
                lastErr = e
            }
        }

        throw Exception("Failed to fetch stream details (HTTP $lastCode)")
    }

    fun getHlsQualities(
        url: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ): List<HlsQuality> {
        if (!url.lowercase().contains("m3u8")) return emptyList()

        return try {
            val m3u8Content = try {
                fetchStreamContent(url, userAgent, cookies, referer)
            } catch (e: Exception) {
                return emptyList()
            }

            val lines = m3u8Content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val qualities = mutableListOf<HlsQuality>()

            var currentResolution = ""
            var currentBandwidth = 0L
            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    currentBandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(line)
                    val resolution = resMatch?.groupValues?.get(1) ?: run {
                        when {
                            currentBandwidth > 4000000 -> "1080p"
                            currentBandwidth > 2000000 -> "720p"
                            currentBandwidth > 900000 -> "480p"
                            currentBandwidth > 400000 -> "360p"
                            else -> "360p"
                        }
                    }
                    currentResolution = resolution
                } else if (!line.startsWith("#") && currentResolution.isNotEmpty()) {
                    val absoluteUrl = resolveAbsoluteUrl(url, line)
                    qualities.add(HlsQuality(absoluteUrl, currentResolution, currentBandwidth))
                    currentResolution = ""
                    currentBandwidth = 0L
                }
            }
            qualities.distinctBy { it.resolution }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun downloadFile(
        context: Context,
        url: String,
        fileName: String,
        coroutineScope: CoroutineScope,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null,
        fallbackUrl: String? = null
    ) {
        DownloadService.startDownload(
            context = context,
            url = url,
            fileName = fileName,
            userAgent = userAgent,
            cookies = cookies,
            referer = referer,
            fallbackUrl = fallbackUrl
        )
    }

    fun downloadFileFromService(
        context: Context,
        service: DownloadService,
        url: String,
        fileName: String,
        coroutineScope: CoroutineScope,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null,
        fallbackUrl: String? = null
    ) {
        registerReceiverIfNeeded(context)
        createNotificationChannel(context)

        val downloadId = java.util.UUID.randomUUID().toString()
        val notificationId = NOTIFICATION_ID_BASE + (_activeDownloads.value.size % 1000)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Starting download: $fileName")
            .setContentText("0%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, false)

        val pauseIntent = Intent("com.example.download.ACTION_TOGGLE_PAUSE").apply {
            putExtra("download_id", downloadId)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent("com.example.download.ACTION_CANCEL").apply {
            putExtra("download_id", downloadId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2 + 1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        builders[downloadId] = builder
        service.startForeground(notificationId, builder.build())

        val downloadInfo = DownloadInfo(
            id = downloadId,
            title = fileName,
            progress = 0
        )
        _activeDownloads.value = _activeDownloads.value + downloadInfo

        val job = coroutineScope.launch(Dispatchers.IO) {
            var activeUrl = url
            var downloadSuccess = false
            var finalFile: File? = null
            var errorToThrow: Exception? = null

            try {
                var targetFile: File? = null
                try {
                    targetFile = if (activeUrl.lowercase().contains("m3u8")) {
                        downloadHls(
                            context = context,
                            m3u8Url = activeUrl,
                            fileName = fileName,
                            notificationId = notificationId,
                            builder = builder,
                            notificationManager = notificationManager,
                            downloadId = downloadId,
                            userAgent = userAgent,
                            cookies = cookies,
                            referer = referer
                        )
                    } else {
                        downloadStandardFile(
                            context = context,
                            fileUrl = activeUrl,
                            fileName = fileName,
                            notificationId = notificationId,
                            builder = builder,
                            notificationManager = notificationManager,
                            downloadId = downloadId,
                            userAgent = userAgent,
                            cookies = cookies,
                            referer = referer
                        )
                    }

                    if (targetFile != null && targetFile.exists()) {
                        val length = targetFile.length()
                        if (length < 5_000_000) {
                            targetFile.delete()
                            throw Exception("File is too small (${length / 1024} KB). Quality link may have expired or is invalid.")
                        }
                    }
                    finalFile = targetFile
                    downloadSuccess = true
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    errorToThrow = e
                }

                // Auto fallback retry if primary stream fails
                if (!downloadSuccess && !fallbackUrl.isNullOrBlank() && fallbackUrl != activeUrl) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Selected quality link failed. Retrying with default stream...", Toast.LENGTH_SHORT).show()
                    }
                    activeUrl = fallbackUrl
                    builder.setContentTitle("Retrying Download with Fallback...")
                    notificationManager.notify(notificationId, builder.build())

                    try {
                        targetFile = if (activeUrl.lowercase().contains("m3u8")) {
                            downloadHls(
                                context = context,
                                m3u8Url = activeUrl,
                                fileName = fileName,
                                notificationId = notificationId,
                                builder = builder,
                                notificationManager = notificationManager,
                                downloadId = downloadId,
                                userAgent = userAgent,
                                cookies = cookies,
                                referer = referer
                            )
                        } else {
                            downloadStandardFile(
                                context = context,
                                fileUrl = activeUrl,
                                fileName = fileName,
                                notificationId = notificationId,
                                builder = builder,
                                notificationManager = notificationManager,
                                downloadId = downloadId,
                                userAgent = userAgent,
                                cookies = cookies,
                                referer = referer
                            )
                        }

                        if (targetFile != null && targetFile.exists()) {
                            val length = targetFile.length()
                            if (length < 5_000_000) {
                                targetFile.delete()
                                throw Exception("Fallback file also too small (${length / 1024} KB).")
                            }
                        }
                        finalFile = targetFile
                        downloadSuccess = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        errorToThrow = e
                    }
                }

                if (!downloadSuccess) {
                    throw errorToThrow ?: Exception("Failed to download video stream.")
                }

                withContext(Dispatchers.Main) {
                    builder.setContentTitle("Download Complete: $fileName")
                        .setContentText("Saved to Downloads (${formatByteSize(finalFile?.length() ?: 0L)})")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setOngoing(false)
                        .setProgress(0, 0, false)
                        .clearActions()
                    notificationManager.notify(notificationId, builder.build())
                    Toast.makeText(context, "Download completed: $fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        builder.setContentTitle("Download Failed: $fileName")
                            .setContentText(e.message ?: "Error")
                            .setSmallIcon(android.R.drawable.stat_notify_error)
                            .setOngoing(false)
                            .setProgress(0, 0, false)
                            .clearActions()
                        notificationManager.notify(notificationId, builder.build())
                        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _activeDownloads.value = _activeDownloads.value.filter { it.id != downloadId }
                builders.remove(downloadId)
                if (_activeDownloads.value.isEmpty()) {
                    service.stopForeground(Service.STOP_FOREGROUND_DETACH)
                }
            }
        }

        _activeDownloads.value = _activeDownloads.value.map {
            if (it.id == downloadId) it.copy(job = job) else it
        }
    }

    fun togglePauseDownload(context: Context, downloadId: String) {
        val currentList = _activeDownloads.value
        val download = currentList.find { it.id == downloadId } ?: return
        val newPaused = !download.isPaused
        _activeDownloads.value = currentList.map {
            if (it.id == downloadId) it.copy(isPaused = newPaused) else it
        }
    }

    fun cancelDownload(downloadId: String) {
        val currentList = _activeDownloads.value
        val download = currentList.find { it.id == downloadId } ?: return
        download.isCancelled = true
        download.job?.cancel()
        _activeDownloads.value = currentList.filter { it.id != downloadId }
    }

    private fun checkCancellationAndPause(downloadId: String) {
        val download = _activeDownloads.value.find { it.id == downloadId }
        if (download != null) {
            if (download.isCancelled) {
                throw CancellationException("Download cancelled by user")
            }
            while (download.isPaused && !download.isCancelled) {
                Thread.sleep(500)
                val current = _activeDownloads.value.find { it.id == downloadId }
                if (current == null || current.isCancelled) {
                    throw CancellationException("Download cancelled by user")
                }
                if (!current.isPaused) break
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotificationProgress(
        context: Context,
        downloadId: String,
        percent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        builder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        notificationId: Int
    ) {
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.id == downloadId) it.copy(progress = percent, downloadedBytes = downloadedBytes, totalBytes = totalBytes) else it
        }
        builder.setProgress(100, percent, false)
            .setContentText("$percent% • " + formatByteSize(downloadedBytes) + (if (totalBytes > 0) " / " + formatByteSize(totalBytes) else ""))
        notificationManager.notify(notificationId, builder.build())
    }

    private fun getRequest(
        url: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ): Request {
        val reqBuilder = Request.Builder().url(url)
        val ua = userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        reqBuilder.header("User-Agent", ua)
        reqBuilder.header("Accept", "*/*")
        reqBuilder.header("Accept-Language", "en-US,en;q=0.9")
        reqBuilder.header("sec-ch-ua", "\"Google Chrome\";v=\"124\", \"Chromium\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
        reqBuilder.header("sec-ch-ua-mobile", "?0")
        reqBuilder.header("sec-ch-ua-platform", "\"Windows\"")
        reqBuilder.header("Sec-Fetch-Dest", "empty")
        reqBuilder.header("Sec-Fetch-Mode", "cors")
        reqBuilder.header("Sec-Fetch-Site", "cross-site")

        val effectiveReferer = if (!referer.isNullOrBlank()) referer else resolveDefaultReferer(url)
        reqBuilder.header("Referer", effectiveReferer)
        try {
            val refHost = Uri.parse(effectiveReferer).host
            if (!refHost.isNullOrBlank()) {
                reqBuilder.header("Origin", "https://$refHost")
            }
        } catch (e: Exception) {}

        val finalCookies = cookies ?: try {
            android.webkit.CookieManager.getInstance().getCookie(url)
        } catch (e: Exception) { null }

        if (!finalCookies.isNullOrBlank()) {
            reqBuilder.header("Cookie", finalCookies)
        }
        return reqBuilder.build()
    }

    private fun downloadHls(
        context: Context,
        m3u8Url: String,
        fileName: String,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        downloadId: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ): File? {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val outFileName = if (fileName.endsWith(".m3u8")) fileName.replace(".m3u8", ".mp4") else fileName
        var targetFile = File(downloadsDir, outFileName)
        var index = 1
        val baseName = outFileName.substringBeforeLast(".")
        val extension = outFileName.substringAfterLast(".", "mp4")
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "$baseName($index).$extension")
            index++
        }

        val m3u8Content = fetchStreamContent(m3u8Url, userAgent, cookies, referer)

        val lines = m3u8Content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var mediaPlaylistUrl = m3u8Url

        // If this is a master playlist, find the matching quality variant stream
        val isMasterPlaylist = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        if (isMasterPlaylist) {
            val variants = mutableListOf<HlsQuality>()
            var curRes = ""
            var curBw = 0L
            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val res = Regex("RESOLUTION=(\\d+x\\d+)").find(line)?.groupValues?.get(1) ?: run {
                        when {
                            bw > 4000000 -> "1080p"
                            bw > 2000000 -> "720p"
                            bw > 900000 -> "480p"
                            else -> "360p"
                        }
                    }
                    curRes = res
                    curBw = bw
                } else if (!line.startsWith("#") && curRes.isNotEmpty()) {
                    val absoluteVariantUrl = resolveAbsoluteUrl(m3u8Url, line)
                    variants.add(HlsQuality(absoluteVariantUrl, curRes, curBw))
                    curRes = ""
                    curBw = 0L
                }
            }

            // Match user's selected resolution based on fileName or target quality
            val lowerFileName = fileName.lowercase()
            val matchedVariant = when {
                lowerFileName.contains("360") -> variants.find { it.resolution.contains("360") || it.resolution.contains("640x360") }
                    ?: variants.minByOrNull { it.bandwidth }
                lowerFileName.contains("480") -> variants.find { it.resolution.contains("480") || it.resolution.contains("854x480") || it.resolution.contains("848x480") }
                    ?: variants.find { it.bandwidth in 500000..1500000 }
                lowerFileName.contains("720") -> variants.find { it.resolution.contains("720") || it.resolution.contains("1280x720") }
                    ?: variants.find { it.bandwidth in 1500000..3500000 }
                lowerFileName.contains("1080") -> variants.find { it.resolution.contains("1080") || it.resolution.contains("1920x1080") }
                    ?: variants.maxByOrNull { it.bandwidth }
                else -> variants.firstOrNull()
            } ?: variants.firstOrNull()

            if (matchedVariant != null) {
                mediaPlaylistUrl = matchedVariant.url
                val childContent = try {
                    fetchStreamContent(mediaPlaylistUrl, userAgent, cookies, referer)
                } catch (e: Exception) {
                    null
                }
                if (childContent != null) {
                    return downloadMediaPlaylist(context, mediaPlaylistUrl, childContent, targetFile, notificationId, builder, notificationManager, downloadId, userAgent, cookies, referer)
                }
            }
        } else {
            val childPlaylist = lines.firstOrNull { !it.startsWith("#") && (it.contains(".m3u8") || it.contains("index")) }
            if (childPlaylist != null) {
                mediaPlaylistUrl = resolveAbsoluteUrl(m3u8Url, childPlaylist)
                val childContent = try {
                    fetchStreamContent(mediaPlaylistUrl, userAgent, cookies, referer)
                } catch (e: Exception) {
                    null
                }
                if (childContent != null) {
                    return downloadMediaPlaylist(context, mediaPlaylistUrl, childContent, targetFile, notificationId, builder, notificationManager, downloadId, userAgent, cookies, referer)
                }
            }
        }

        return downloadMediaPlaylist(context, mediaPlaylistUrl, m3u8Content, targetFile, notificationId, builder, notificationManager, downloadId, userAgent, cookies, referer)
    }

    private fun downloadMediaPlaylist(
        context: Context,
        playlistUrl: String,
        playlistContent: String,
        targetFile: File,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        downloadId: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ): File? {
        val lines = playlistContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val segmentUrls = mutableListOf<String>()
        for (line in lines) {
            if (!line.startsWith("#")) {
                segmentUrls.add(resolveAbsoluteUrl(playlistUrl, line))
            }
        }
        if (segmentUrls.isEmpty()) {
            throw Exception("No video segments found in the playlist.")
        }

        val outputStream = FileOutputStream(targetFile)
        val totalSegments = segmentUrls.size
        var downloadedSegments = 0
        var totalBytesDownloadedSoFar = 0L
        val buffer = ByteArray(65536)
        var segmentDelayMs = 120L
        var lastUpdateMs = 0L

        try {
            for (i in 0 until totalSegments) {
                checkCancellationAndPause(downloadId)
                val segmentUrl = segmentUrls[i]
                var success = false
                var retries = 3

                try {
                    Thread.sleep(segmentDelayMs)
                } catch (e: Exception) {}

                while (!success && retries > 0) {
                    checkCancellationAndPause(downloadId)
                    try {
                        val host = try { Uri.parse(segmentUrl).host } catch (e: Exception) { null } ?: ""
                        val segRequests = mutableListOf<Request>()
                        val effectiveSegRef = if (!referer.isNullOrBlank()) referer else resolveDefaultReferer(segmentUrl)
                        segRequests.add(getRequest(segmentUrl, userAgent, cookies, effectiveSegRef))

                        val isAnikotoOrMegaplay = segmentUrl.contains("megaplay") || segmentUrl.contains("kryntal") || segmentUrl.contains("anikoto") ||
                                effectiveSegRef.contains("anikoto") || effectiveSegRef.contains("megaplay") || effectiveSegRef.contains("kryntal")

                        if (isAnikotoOrMegaplay) {
                            for (ref in listOf("https://megaplay.buzz/", "https://anikoto.cz/", "https://kryntal.top/", "https://dokicloud.one/")) {
                                segRequests.add(
                                    Request.Builder()
                                        .url(segmentUrl)
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                        .header("Accept", "*/*")
                                        .header("Accept-Language", "en-US,en;q=0.9")
                                        .header("Referer", ref)
                                        .header("Origin", ref.removeSuffix("/"))
                                        .header("Sec-Fetch-Dest", "empty")
                                        .header("Sec-Fetch-Mode", "cors")
                                        .header("Sec-Fetch-Site", "cross-site")
                                        .build()
                                )
                            }
                        }

                        if (host.isNotEmpty()) {
                            segRequests.add(
                                Request.Builder()
                                    .url(segmentUrl)
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                    .header("Accept", "*/*")
                                    .header("Accept-Language", "en-US,en;q=0.9")
                                    .header("Referer", "https://$host/")
                                    .header("Origin", "https://$host")
                                    .header("Sec-Fetch-Dest", "empty")
                                    .header("Sec-Fetch-Mode", "cors")
                                    .header("Sec-Fetch-Site", "cross-site")
                                    .build()
                            )
                        }
                        segRequests.add(
                            Request.Builder()
                                .url(segmentUrl)
                                .header("User-Agent", "ExoPlayerLib/2.19.1 (Linux; Android 14)")
                                .header("Accept", "*/*")
                                .build()
                        )

                        var segResp: Response? = null
                        for (req in segRequests) {
                            try {
                                val r = okHttpClient.newCall(req).execute()
                                if (r.isSuccessful) {
                                    segResp = r
                                    break
                                }
                                if (r.code == 429) {
                                    r.close()
                                    segmentDelayMs = (segmentDelayMs * 2).coerceAtMost(2000L)
                                    Thread.sleep(segmentDelayMs)
                                    break
                                }
                                r.close()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                            }
                        }

                        if (segResp != null && segResp.isSuccessful) {
                            val byteStream = segResp.body?.byteStream() ?: throw Exception("Empty segment")
                            var bytesRead: Int
                            while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                                checkCancellationAndPause(downloadId)
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesDownloadedSoFar += bytesRead
                                val currentMs = System.currentTimeMillis()
                                if (currentMs - lastUpdateMs > 300) {
                                    val percent = ((downloadedSegments * 100) / totalSegments).coerceIn(0, 100)
                                    val approxTotalBytes = if (downloadedSegments > 0) {
                                        (totalBytesDownloadedSoFar * totalSegments) / downloadedSegments
                                    } else -1L
                                    updateNotificationProgress(context, downloadId, percent, totalBytesDownloadedSoFar, approxTotalBytes, builder, notificationManager, notificationId)
                                    lastUpdateMs = currentMs
                                }
                            }
                            byteStream.close()
                            segResp.close()
                            success = true
                            if (segmentDelayMs > 120L) {
                                segmentDelayMs -= 10L
                            }
                        } else {
                            retries--
                            Thread.sleep(300)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        retries--
                        Thread.sleep(500)
                    }
                }

                if (!success) {
                    throw Exception("Failed to download video segment $i after retries.")
                }

                downloadedSegments++
                val percent = ((downloadedSegments * 100) / totalSegments).coerceIn(0, 100)
                val estimatedTotalBytes = if (downloadedSegments > 0) {
                    (totalBytesDownloadedSoFar * totalSegments) / downloadedSegments
                } else -1L
                updateNotificationProgress(context, downloadId, percent, totalBytesDownloadedSoFar, estimatedTotalBytes, builder, notificationManager, notificationId)
            }
        } finally {
            try {
                outputStream.flush()
                outputStream.close()
            } catch (e: Exception) {}
        }

        if (targetFile.exists() && targetFile.length() < 10_000) {
            val length = targetFile.length()
            targetFile.delete()
            throw Exception("Downloaded HLS file is invalid (${length} bytes). Stream may be expired or blocked.")
        }

        return targetFile
    }

    private fun downloadStandardFile(
        context: Context,
        fileUrl: String,
        fileName: String,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        downloadId: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ): File? {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var targetFile = File(downloadsDir, fileName)
        var index = 1
        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "mp4")
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "$baseName($index).$extension")
            index++
        }

        var response: Response? = null
        var retries = 3
        var delayMs = 1000L
        var lastStatusCode: Int? = null
        var lastException: Exception? = null

        while (retries > 0 && response == null) {
            checkCancellationAndPause(downloadId)
            val effectiveFileRef = if (!referer.isNullOrBlank()) referer else resolveDefaultReferer(fileUrl)
            val candidateReqs = mutableListOf<Request>()
            candidateReqs.add(getRequest(fileUrl, userAgent, cookies, effectiveFileRef))

            val isAnikotoOrMegaplay = fileUrl.contains("megaplay") || fileUrl.contains("kryntal") || fileUrl.contains("anikoto") ||
                    effectiveFileRef.contains("anikoto") || effectiveFileRef.contains("megaplay") || effectiveFileRef.contains("kryntal")

            if (isAnikotoOrMegaplay) {
                for (ref in listOf("https://megaplay.buzz/", "https://anikoto.cz/", "https://kryntal.top/", "https://dokicloud.one/")) {
                    candidateReqs.add(
                        Request.Builder()
                            .url(fileUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .header("Accept", "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Referer", ref)
                            .header("Origin", ref.removeSuffix("/"))
                            .header("Sec-Fetch-Dest", "empty")
                            .header("Sec-Fetch-Mode", "cors")
                            .header("Sec-Fetch-Site", "cross-site")
                            .build()
                    )
                }
            }

            val uri = try { Uri.parse(fileUrl) } catch (ex: Exception) { null }
            val host = uri?.host ?: ""
            if (host.isNotEmpty()) {
                val cookieVal = cookies ?: try {
                    android.webkit.CookieManager.getInstance().getCookie(fileUrl)
                } catch (ex: Exception) { null }

                val b = Request.Builder()
                    .url(fileUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "https://$host/")
                    .header("Origin", "https://$host")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "cross-site")
                if (!cookieVal.isNullOrBlank()) {
                    b.header("Cookie", cookieVal)
                }
                candidateReqs.add(b.build())
            }

            for (req in candidateReqs) {
                try {
                    val resp = okHttpClient.newCall(req).execute()
                    lastStatusCode = resp.code
                    if (resp.isSuccessful) {
                        response = resp
                        break
                    }
                    if (resp.code == 429) {
                        resp.close()
                        retries--
                        if (retries > 0) {
                            Thread.sleep(delayMs)
                            delayMs *= 2
                        }
                        break
                    }
                    resp.close()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastException = e
                }
            }

            retries--
            if (retries > 0) {
                Thread.sleep(delayMs)
                delayMs *= 2
            }
        }

        val finalResponse = response ?: run {
            val errorMsg = when {
                lastStatusCode == 403 -> "Server Access Forbidden (HTTP 403). The video hosting server is blocking automated downloads. Please try a different option/quality."
                lastStatusCode == 404 -> "Video File Not Found on server (HTTP 404). Please try a different server or quality option."
                lastStatusCode == 429 -> "Server Rate Limited (HTTP 429). Please wait a few seconds and try downloading again."
                lastStatusCode != null -> "Download failed with server response (HTTP $lastStatusCode). Please try another option."
                lastException != null -> "Connection failed: ${lastException.localizedMessage ?: lastException.message ?: "timeout"}"
                else -> "Failed to establish a reliable connection to server. Please try again in a few minutes."
            }
            throw Exception(errorMsg)
        }

        finalResponse.use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 403 || resp.code == 401) {
                    throw Exception("Server requires interactive selection. Please use the In-App Downloader Window to select quality.")
                }
                if (resp.code == 429) {
                    throw Exception("Server limit reached (HTTP 429). Please wait a few seconds and try downloading again.")
                }
                throw Exception("Failed to download file (HTTP ${resp.code})")
            }

            val contentType = resp.header("Content-Type")?.lowercase() ?: ""
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                throw Exception("Server page detected. Please use the In-App Downloader Window to select quality and download.")
            }

            val totalBytes = resp.body?.contentLength() ?: -1L
            val rawByteStream = resp.body?.byteStream() ?: throw Exception("Empty response body")

            val byteStream = java.io.BufferedInputStream(rawByteStream)
            byteStream.mark(4096)
            val previewBytes = ByteArray(1024)
            val previewRead = byteStream.read(previewBytes)
            byteStream.reset()

            val previewStr = if (previewRead > 0) String(previewBytes, 0, previewRead) else ""
            if (previewStr.contains("#EXTM3U") || previewStr.contains("#EXT-X-STREAM-INF")) {
                val playlistContent = byteStream.bufferedReader().use { it.readText() }
                return downloadMediaPlaylist(
                    context = context,
                    playlistUrl = fileUrl,
                    playlistContent = playlistContent,
                    targetFile = targetFile,
                    notificationId = notificationId,
                    builder = builder,
                    notificationManager = notificationManager,
                    downloadId = downloadId,
                    userAgent = userAgent,
                    cookies = cookies,
                    referer = referer
                )
            }

            val outputStream = FileOutputStream(targetFile)
            var bytesCopied = 0L
            val buffer = ByteArray(65536)
            var bytesRead: Int
            var lastUpdateMs = System.currentTimeMillis()

            try {
                while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                    checkCancellationAndPause(downloadId)
                    outputStream.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead

                    val currentMs = System.currentTimeMillis()
                    if (currentMs - lastUpdateMs > 300) {
                        val percent = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                        updateNotificationProgress(context, downloadId, percent, bytesCopied, totalBytes, builder, notificationManager, notificationId)
                        lastUpdateMs = currentMs
                    }
                }
            } finally {
                outputStream.flush()
                outputStream.close()
                byteStream.close()
            }

            if (bytesCopied < 10_000) {
                val len = targetFile.length()
                targetFile.delete()
                throw Exception("Downloaded file is invalid ($len bytes). Stream link may have expired or is invalid.")
            }
        }

        return targetFile
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }
        val baseUri = URI(baseUrl)
        return baseUri.resolve(relativeUrl).toString()
    }
}
