package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.graphics.BitmapFactory
import com.example.MainActivity
import com.example.R


data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val storePageUrl: String,
    val forceUpdate: Boolean,
    val minRequiredVersionCode: Int,
    val message: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateManager {
    fun showUpdateNotification(context: Context, info: UpdateInfo, currentVersion: String) {
        val currentCode = getAppVersionCode(context)
        if (info.latestVersionCode <= currentCode) {
            return // Never notify if not genuinely a newer version
        }
        val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
        val lastNotifiedCode = prefs.getInt("last_notified_version_code", 0)
        if (lastNotifiedCode >= info.latestVersionCode) {
            return // Already notified for this version, do not spam duplicate notifications
        }
        prefs.edit().putInt("last_notified_version_code", info.latestVersionCode).apply()

        val channelId = "app_update_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for available app updates"
            }
            notificationManager.createNotificationChannel(channel)
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

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(android.graphics.Color.parseColor("#FF6D00"))
            .apply {
                val largeIcon = getAppIconBitmap(context)
                if (largeIcon != null) {
                    setLargeIcon(largeIcon)
                }
            }
            .setContentTitle("New Update Available (v${info.latestVersionName})")
            .setContentText("Current version: v$currentVersion. New version: v${info.latestVersionName}. Tap to update.")
            .setSubText("App Update System")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.releaseNotes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(10101, builder.build())
    }

    private const val DOWNLOAD_CHANNEL_ID = "media_downloads_channel"
    private const val UPDATE_COMPLETE_CHANNEL_ID = "update_complete_channel"

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

            val completeName = "App Updates Complete"
            val completeDesc = "Notifications when app update download is complete."
            val completeChannel = NotificationChannel(UPDATE_COMPLETE_CHANNEL_ID, completeName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = completeDesc
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(completeChannel)
        }
    }


    private const val UPDATE_API_BASE_URL = "https://xubilasappshub.xubilaswebdevcorp.shop/api/v1/check-update"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("X-App-Version", com.example.BuildConfig.VERSION_CODE.toString())
                    .build()
                chain.proceed(request)
            }
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
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("HTTP Error: ${response.code}")
                }
                val bodyString = response.body?.string() ?: return@withContext UpdateCheckResult.Error("Empty server response.")
                val json = JSONObject(bodyString)

                val latestVersionCode = json.optInt("latest_version_code", json.optInt("version_code", 0))
                val latestVersionName = json.optString("latest_version_name", json.optString("version_name", "1.0.1"))
                val minRequiredVersionCode = json.optInt("min_required_version_code", json.optInt("min_version_code", 0))
                val messageText = json.optString("message", json.optString("description", ""))
                val rawDescription = json.optString("description", "")
                
                // Extract Release Notes / What's New from all possible JSON fields returned by AppsHub API
                val extractedReleaseNotes = sequenceOf(
                    json.optString("release_notes", ""),
                    json.optString("releaseNotes", ""),
                    json.optString("whats_new", ""),
                    json.optString("whatsNew", ""),
                    json.optString("changelog", ""),
                    json.optString("change_log", ""),
                    json.optString("update_notes", ""),
                    json.optString("updateNotes", ""),
                    json.optString("notes", ""),
                    json.optString("changes", "")
                ).map { it.trim() }.firstOrNull { it.isNotBlank() } ?: ""

                val releaseNotes = when {
                    extractedReleaseNotes.isNotBlank() -> extractedReleaseNotes
                    messageText.isNotBlank() -> messageText
                    rawDescription.isNotBlank() -> rawDescription
                    else -> "• Performance improvements and bug fixes."
                }
                var apkDownloadUrl = json.optString("update_url", json.optString("apk_download_url", json.optString("download_url", "https://xubilasappshub.xubilaswebdevcorp.shop/downloads/app-release.apk")))
                
                // TEMPORARY FIX: Intercept the broken Vercel frontend route and use the direct R2 URL
                if (apkDownloadUrl == "https://xubilasappshub.xubilaswebdevcorp.shop/downloads/app-release.apk" || apkDownloadUrl.isBlank()) {
                    apkDownloadUrl = "https://pub-c51f69965b3743c883eff00e8daf1a83.r2.dev/apks/home-air-tv-v2-0-1784664666076.apk"
                }

                val rawStorePageUrl = json.optString("store_page_url", json.optString("app_url", ""))
                val storePageUrl = if (rawStorePageUrl.isBlank() || rawStorePageUrl == "https://xubilasappshub.xubilaswebdevcorp.shop/") {
                    "https://xubilasappshub.xubilaswebdevcorp.shop/app/home-air-tv#download"
                } else {
                    rawStorePageUrl
                }
                val forceUpdate = (currentVersionCode < minRequiredVersionCode) || json.optBoolean("force_update", false)

                if (latestVersionCode > currentVersionCode || currentVersionCode < minRequiredVersionCode) {
                    val info = UpdateInfo(
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkDownloadUrl,
                        storePageUrl = storePageUrl,
                        forceUpdate = forceUpdate,
                        minRequiredVersionCode = minRequiredVersionCode,
                        message = messageText
                    )
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
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val uri = Uri.parse(downloadUrl)
            
            // Delete old update file if exists
            val destinationDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            val apkFile = File(destinationDir, "app_update.apk")
            if (apkFile.exists()) apkFile.delete()

            val request = android.app.DownloadManager.Request(uri).apply {
                setTitle("Downloading Update")
                setDescription("Downloading Home Air TV update...")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, "app_update.apk")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            var isDownloading = true
            var success = false

            while (isDownloading) {
                val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor != null && cursor.moveToFirst()) {
                    val statusColumn = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    val totalBytesColumn = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val bytesDownloadedColumn = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    
                    if (statusColumn >= 0 && totalBytesColumn >= 0 && bytesDownloadedColumn >= 0) {
                        val status = cursor.getInt(statusColumn)
                        
                        when (status) {
                            android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                                isDownloading = false
                                success = true
                                withContext(Dispatchers.Main) { onProgress(100) }
                            }
                            android.app.DownloadManager.STATUS_FAILED -> {
                                isDownloading = false
                                success = false
                            }
                            android.app.DownloadManager.STATUS_RUNNING -> {
                                val total = cursor.getLong(totalBytesColumn)
                                val downloaded = cursor.getLong(bytesDownloadedColumn)
                                if (total > 0) {
                                    val progress = ((downloaded * 100) / total).toInt()
                                    withContext(Dispatchers.Main) { onProgress(progress) }
                                }
                            }
                        }
                    }
                    cursor.close()
                } else {
                    // Download might have been cancelled
                    isDownloading = false
                }
                
                if (isDownloading) {
                    kotlinx.coroutines.delay(1000)
                }
            }

            if (success && apkFile.exists()) {
                createDownloadNotificationChannel(context)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notificationId = 20202 + downloadUrl.hashCode().coerceAtLeast(0)

                val authority = "${context.packageName}.fileprovider"
                val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                
                val installPendingIntent = PendingIntent.getActivity(context, notificationId, installIntent, pendingIntentFlags)

                val successBuilder = NotificationCompat.Builder(context, UPDATE_COMPLETE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setColor(android.graphics.Color.parseColor("#FF6D00"))
                    .apply {
                        val largeIcon = getAppIconBitmap(context)
                        if (largeIcon != null) {
                            setLargeIcon(largeIcon)
                        }
                    }
                    .setContentTitle("Update Download Complete")
                    .setContentText("Tap here to install the new version.")
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setContentIntent(installPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)

                notificationManager.notify(notificationId, successBuilder.build())

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
                return@withContext apkFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Triggers native Android Package Installer for the downloaded APK file.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        try {
            // Android 8.0+ Unknown sources check
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

    /**
     * Checks if the app can install unknown package sources on Android 8.0+.
     */
    fun canInstallUnknownPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens Settings to request permission for unknown app sources.
     */
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

    private fun getAppIconBitmap(context: Context): android.graphics.Bitmap? {
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}
