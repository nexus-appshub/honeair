with open("app/src/main/java/com/example/update/AppUpdateManager.kt", "r") as f:
    content = f.read()

imports = """
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.graphics.BitmapFactory
import com.example.MainActivity
import com.example.R
"""

content = content.replace("import java.util.concurrent.TimeUnit", "import java.util.concurrent.TimeUnit" + imports)

# First add the Notification logic in checkForUpdate. Wait, I shouldn't just do it in checkForUpdate directly unless requested.
# But it's fine, let's create a function to show the notification.
new_functions = """
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
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
"""

content = content.replace("object AppUpdateManager {", "object AppUpdateManager {" + new_functions)

# Now inject download notification into downloadApk.
# Instead of text replacement which is brittle, I'll rewrite downloadApk.

download_apk_start = "suspend fun downloadApk("
download_apk_end = "        return@withContext null\n    }"

idx_start = content.find(download_apk_start)
idx_end = content.find("fun installApk(context: Context, apkFile: File): Boolean {")

if idx_start != -1 and idx_end != -1:
    old_download_apk = content[idx_start:idx_end]
    new_download_apk = """suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            createDownloadNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = 20202 + downloadUrl.hashCode().coerceAtLeast(0)

            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
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
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Download Complete")
                    .setContentText("app_update.apk")
                    .setAutoCancel(true)
                    .setOngoing(false)

                notificationManager.notify(notificationId, successBuilder.build())

                return@withContext apkFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Optional: Show error notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = 20202 + downloadUrl.hashCode().coerceAtLeast(0)
            notificationManager.cancel(notificationId)
        }
        return@withContext null
    }

    /**
     * """
    
    content = content[:idx_start] + new_download_apk + content[idx_end + 7:] # adding length of comment start to match

    # Wait, in the return `return@withContext UpdateAvailable(...)`, I should call `showUpdateNotification`.
    # It's better if `MainActivity` or the caller handles it, but since the user asks for it in `AppUpdateManager`, let's just make it show when `checkForUpdate` returns.
    # Wait, `checkForUpdate` currently has:
    # return@withContext UpdateCheckResult.UpdateAvailable(...)
    # Let's replace that.
    
    # Let's just update `checkForUpdate` to trigger the notification.
    update_trigger = """
                if (latestVersionCode > currentVersionCode && latestVersionName != currentVersionName) {
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
                }"""
    
    # replace the if block
    import re
    content = re.sub(r'if \(latestVersionCode > currentVersionCode && latestVersionName != currentVersionName\) \{[\s\S]*?return@withContext UpdateCheckResult.UpToDate\s*\}',
                     update_trigger + """ else {
                    return@withContext UpdateCheckResult.UpToDate
                }""", content)

with open("app/src/main/java/com/example/update/AppUpdateManager.kt", "w") as f:
    f.write(content)

