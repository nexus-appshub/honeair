import re

content = """package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

data class DownloadInfo(
    val id: String,
    val title: String,
    val progress: Int,
    var isCancelled: Boolean = false,
    val job: Job? = null
)

object MediaDownloader {
    private const val CHANNEL_ID = "media_downloads_channel"
    private const val CHANNEL_NAME = "Media Downloads"
    private const val NOTIFICATION_ID_BASE = 1000

    private val _activeDownloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadInfo>> = _activeDownloads

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("X-App-Version", com.example.BuildConfig.VERSION_CODE.toString())
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for in-app media downloads."
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun cancelDownload(id: String) {
        val list = _activeDownloads.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = list[index]
            item.job?.cancel()
            list[index] = item.copy(isCancelled = true)
            _activeDownloads.value = list
        }
    }
    
    private fun removeDownload(id: String) {
        _activeDownloads.value = _activeDownloads.value.filter { it.id != id }
    }

    private fun updateDownloadProgress(id: String, progress: Int) {
        val list = _activeDownloads.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = list[index].copy(progress = progress)
            _activeDownloads.value = list
        }
    }

    fun downloadFile(
        context: Context,
        url: String,
        fileName: String,
        coroutineScope: CoroutineScope
    ) {
        val isEmbedPage = url.contains("vidsrc") || url.contains("vidlink") ||
                url.contains("embed.su") || url.contains("multiembed") ||
                url.contains("smashy.stream") || url.contains("vidbinge") ||
                url.contains("player.autoembed.cc")

        val hasVideoExtension = url.lowercase().contains(".m3u8") ||
                url.lowercase().contains(".mp4") ||
                url.lowercase().contains(".mkv") ||
                url.lowercase().contains(".ts")

        if (isEmbedPage && !hasVideoExtension) {
            Toast.makeText(
                context,
                "Please play the video first to capture and download the high-speed direct stream!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val downloadId = url.hashCode().toString()
        val notificationId = NOTIFICATION_ID_BASE + url.hashCode().coerceAtLeast(0)
        
        // Prevent duplicate downloads
        if (_activeDownloads.value.any { it.id == downloadId }) {
            Toast.makeText(context, "Already downloading this file", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading Video")
            .setContentText("0%")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, false)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        Toast.makeText(context, "Direct Download started...", Toast.LENGTH_SHORT).show()

        val job = coroutineScope.launch(Dispatchers.IO) {
            var downloadSuccess = false
            var finalFile: File? = null

            try {
                if (url.contains(".m3u8")) {
                    finalFile = downloadHls(
                        context = context,
                        m3u8Url = url,
                        fileName = fileName,
                        notificationId = notificationId,
                        builder = builder,
                        notificationManager = notificationManager,
                        downloadId = downloadId
                    )
                } else {
                    finalFile = downloadStandardFile(
                        context = context,
                        fileUrl = url,
                        fileName = fileName,
                        notificationId = notificationId,
                        builder = builder,
                        notificationManager = notificationManager,
                        downloadId = downloadId
                    )
                }
                downloadSuccess = true
            } catch (e: CancellationException) {
                 finalFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: \${e.message}", Toast.LENGTH_LONG).show()
                }
                val failBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Download Failed")
                    .setContentText(e.localizedMessage)
                    .setOngoing(false)
                    .setAutoCancel(true)

                try {
                    notificationManager.notify(notificationId, failBuilder.build())
                } catch (se: SecurityException) {
                    se.printStackTrace()
                }
            } finally {
                removeDownload(downloadId)
            }

            val actualFile = finalFile
            val isCancelledCheck = _activeDownloads.value.find { it.id == downloadId }?.isCancelled == true
            
            if (downloadSuccess && actualFile != null && !isCancelledCheck) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download Complete!\nSaved to Downloads folder.", Toast.LENGTH_LONG).show()
                }

                val pendingIntent = try {
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "\${context.packageName}.fileprovider",
                        actualFile
                    )
                    
                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    
                    PendingIntent.getActivity(context, notificationId, openIntent, flags)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                val successBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Download Complete")
                    .setContentText(actualFile.name)
                    .setOngoing(false)
                    .setAutoCancel(true)
                
                if (pendingIntent != null) {
                    successBuilder.setContentIntent(pendingIntent)
                }

                try {
                    notificationManager.notify(notificationId, successBuilder.build())
                } catch (se: SecurityException) {
                    se.printStackTrace()
                }
            } else if (isCancelledCheck) {
                notificationManager.cancel(notificationId)
            }
        }
        
        val newDownload = DownloadInfo(
            id = downloadId,
            title = fileName,
            progress = 0,
            isCancelled = false,
            job = job
        )
        _activeDownloads.value = _activeDownloads.value + newDownload
    }
    
    private fun checkCancellation(downloadId: String) {
        val isCancelled = _activeDownloads.value.find { it.id == downloadId }?.isCancelled == true
        if (isCancelled) throw CancellationException("Download cancelled by user")
    }

    private fun downloadHls(
        context: Context,
        m3u8Url: String,
        fileName: String,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        downloadId: String
    ): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val outFileName = if (fileName.endsWith(".m3u8")) fileName.replace(".m3u8", ".mp4") else fileName
        var targetFile = File(downloadsDir, outFileName)
        var index = 1
        val baseName = outFileName.substringBeforeLast(".")
        val extension = outFileName.substringAfterLast(".", "mp4")
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "\$baseName(\$index).\$extension")
            index++
        }

        val request = Request.Builder().url(m3u8Url).build()
        val m3u8Content = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch stream details (code \${response.code})")
            response.body?.string() ?: throw Exception("Stream details empty")
        }

        val lines = m3u8Content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var mediaPlaylistUrl = m3u8Url
        val childPlaylist = lines.firstOrNull { !it.startsWith("#") && (it.contains(".m3u8") || it.contains("index")) }
        if (childPlaylist != null) {
            mediaPlaylistUrl = resolveAbsoluteUrl(m3u8Url, childPlaylist)
            val childRequest = Request.Builder().url(mediaPlaylistUrl).build()
            val childContent = okHttpClient.newCall(childRequest).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (childContent != null) {
                return downloadMediaPlaylist(context, mediaPlaylistUrl, childContent, targetFile, notificationId, builder, notificationManager, downloadId)
            }
        }
        
        return downloadMediaPlaylist(context, mediaPlaylistUrl, m3u8Content, targetFile, notificationId, builder, notificationManager, downloadId)
    }

    private fun downloadMediaPlaylist(
        context: Context,
        playlistUrl: String,
        playlistContent: String,
        targetFile: File,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        downloadId: String
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
        val buffer = ByteArray(65536)

        try {
            for (i in 0 until totalSegments) {
                checkCancellation(downloadId)
                val segmentUrl = segmentUrls[i]
                var success = false
                var retries = 3

                while (!success && retries > 0) {
                    checkCancellation(downloadId)
                    try {
                        val segmentRequest = Request.Builder().url(segmentUrl).build()
                        okHttpClient.newCall(segmentRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                val byteStream = response.body?.byteStream() ?: throw Exception("Empty segment")
                                var bytesRead: Int
                                while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                                    checkCancellation(downloadId)
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                                byteStream.close()
                                success = true
                            } else {
                                retries--
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        retries--
                        if (retries <= 0) {
                            throw e
                        }
                        Thread.sleep(300)
                    }
                }
                
                downloadedSegments++
                val percent = (downloadedSegments * 100) / totalSegments
                updateDownloadProgress(downloadId, percent)
                
                builder.setProgress(100, percent, false)
                    .setContentText("\$percent%")
                
                try {
                    notificationManager.notify(notificationId, builder.build())
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        } finally {
            outputStream.flush()
            outputStream.close()
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
        downloadId: String
    ): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var targetFile = File(downloadsDir, fileName)
        var index = 1
        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "")
        val extPart = if (extension.isNotEmpty()) ".\$extension" else ""
        
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "\$baseName(\$index)\$extPart")
            index++
        }

        val request = Request.Builder().url(fileUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download file (code \${response.code})")
            
            val totalBytes = response.body?.contentLength() ?: -1L
            val byteStream = response.body?.byteStream() ?: throw Exception("Empty response body")
            
            val outputStream = FileOutputStream(targetFile)
            var bytesCopied = 0L
            val buffer = ByteArray(65536)
            var bytesRead: Int
            
            var lastUpdateMs = System.currentTimeMillis()
            
            try {
                while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                    checkCancellation(downloadId)
                    outputStream.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    
                    if (totalBytes > 0) {
                        val currentMs = System.currentTimeMillis()
                        if (currentMs - lastUpdateMs > 500) {
                            val percent = ((bytesCopied * 100) / totalBytes).toInt()
                            updateDownloadProgress(downloadId, percent)
                            
                            builder.setProgress(100, percent, false)
                                .setContentText("\$percent%")
                            try {
                                notificationManager.notify(notificationId, builder.build())
                            } catch (e: SecurityException) {}
                            
                            lastUpdateMs = currentMs
                        }
                    }
                }
            } finally {
                outputStream.flush()
                outputStream.close()
                byteStream.close()
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
"""

with open("app/src/main/java/com/example/download/MediaDownloader.kt", "w") as f:
    f.write(content)
