package com.example.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val storePageUrl: String,
    val forceUpdate: Boolean
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateManager {

    fun showUpdateNotification(context: Context, info: UpdateInfo, currentVersion: String) {
        val channelId = "app_update_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for available app updates"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setContentTitle("New Update Available (v${info.latestVersionName})")
            .setContentText("Current version: v$currentVersion. New version: v${info.latestVersionName}. Tap to update.")
            .setSubText("App Update System")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Release Notes:\n${info.releaseNotes}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(10101, builder.build())
    }

    private const val DOWNLOAD_CHANNEL_ID = "media_downloads_channel"

    private fun createDownloadNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Media Downloads"
            val descriptionText = "Shows progress for in-app media downloads."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(DOWNLOAD_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private const val UPDATE_API_BASE_URL = "https://xubilasappshub.xubilaswebdevcorp.shop/api/v1/check-update"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Checks AppsHub Store REST API for a newer version of this application.
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val packageName = context.packageName
            val currentVersionCode = getAppVersionCode(context)
            val currentVersionName = getAppVersionName(context)

            // Dynamic cache-busting timestamp param
            val apiUrl = "$UPDATE_API_BASE_URL?package_name=$packageName&t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "AppsHubUpdateEngine/1.0 ($packageName)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("HTTP Error: ${response.code}")
                }
                val bodyString = response.body?.string() ?: return@withContext UpdateCheckResult.Error("Empty server response.")
                val json = JSONObject(bodyString)

                val latestVersionCode = json.optInt("latest_version_code", json.optInt("version_code", 0))
                val latestVersionName = json.optString("latest_version_name", json.optString("version_name", "1.0.1"))
                
                val rawDescription = json.optString("description", "")
                val rawReleaseNotes = json.optString("release_notes", json.optString("whats_new", json.optString("changelog", json.optString("update_notes", ""))))

                var releaseNotes = rawReleaseNotes.ifBlank { rawDescription }

                // If releaseNotes is identical to the main app description or contains general promo text,
                // check if alternate changelog fields exist, or provide concise version update highlights
                if (releaseNotes.isNotBlank() && (
                        releaseNotes == rawDescription ||
                        releaseNotes.contains("Bring high-quality entertainment", ignoreCase = true) ||
                        releaseNotes.contains("your ultimate companion", ignoreCase = true)
                    )) {
                    val altNotes = json.optString("whats_new", json.optString("changelog", json.optString("update_notes", "")))
                    if (altNotes.isNotBlank() && altNotes != rawDescription && !altNotes.contains("Bring high-quality entertainment", ignoreCase = true)) {
                        releaseNotes = altNotes
                    } else {
                        releaseNotes = "• Performance & streaming playback optimizations.\n• Upgraded live TV stream player and channel sync.\n• Built-in ad-blocking and popup protection."
                    }
                }

                var apkDownloadUrl = json.optString("apk_download_url", json.optString("download_url", "https://xubilasappshub.xubilaswebdevcorp.shop/downloads/app-release.apk"))

                // Intercept broken Vercel frontend route and use direct R2 URL if applicable
                if (apkDownloadUrl == "https://xubilasappshub.xubilaswebdevcorp.shop/downloads/app-release.apk") {
                    apkDownloadUrl = "https://pub-c51f69965b3743c883eff00e8daf1a83.r2.dev/apks/home-air-tv-v2-0-1784664666076.apk"
                }

                val rawStorePageUrl = json.optString("store_page_url", json.optString("app_url", ""))
                val storePageUrl = if (rawStorePageUrl.isBlank() || rawStorePageUrl == "https://xubilasappshub.xubilaswebdevcorp.shop/") {
                    "https://xubilasappshub.xubilaswebdevcorp.shop/app/home-air-tv#download"
                } else {
                    rawStorePageUrl
                }

                val forceUpdate = json.optBoolean("force_update", false)

                if (latestVersionCode > currentVersionCode || latestVersionName != currentVersionName) {
                    val info = UpdateInfo(
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkDownloadUrl,
                        storePageUrl = storePageUrl,
                        forceUpdate = forceUpdate
                    )
                    showUpdateNotification(context, info, currentVersionName)
                    return@withContext UpdateCheckResult.UpdateAvailable(info)
                } else {
                    return@withContext UpdateCheckResult.UpToDate
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateCheckResult.Error(e.message ?: "Unknown connection failure.")
        }
    }

    /**
     * Checks if a downloaded file is a valid ZIP/APK archive by reading its first 4 magic bytes.
     */
    fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        try {
            val fis = java.io.FileInputStream(file)
            val header = ByteArray(4)
            val read = fis.read(header)
            fis.close()
            if (read == 4) {
                // Check ZIP/APK signature PK.. (0x50, 0x4B, 0x03, 0x04)
                return header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Downloads APK file directly to the app's external cache folder with progress tracking.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            createDownloadNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = 20202 + downloadUrl.hashCode().coerceAtLeast(0)

            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle("Downloading Update")
                .setContentText("app_update.apk")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, 0, true)

            notificationManager.notify(notificationId, builder.build())

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    notificationManager.cancel(notificationId)
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()
                val destinationDir = context.externalCacheDir ?: context.cacheDir
                val apkFile = File(destinationDir, "app_update.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        withContext(Dispatchers.Main) {
                            onProgress(progress.coerceIn(0, 100))
                        }
                        builder.setProgress(100, progress, false)
                            .setContentText("Downloading... $progress%")
                        notificationManager.notify(notificationId, builder.build())
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                val successBuilder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setContentTitle("Download Complete")
                    .setContentText("app_update.apk - Tap to install")
                    .setAutoCancel(true)
                    .setOngoing(false)

                notificationManager.notify(notificationId, successBuilder.build())
                return@withContext apkFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = 20202 + downloadUrl.hashCode().coerceAtLeast(0)
            notificationManager.cancel(notificationId)
        }
        return@withContext null
    }

    /**
     * Triggers native Android Package Installer for the downloaded APK file.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return false
                }
            }
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun canInstallUnknownPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun getAppVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    fun getAppVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }
}
