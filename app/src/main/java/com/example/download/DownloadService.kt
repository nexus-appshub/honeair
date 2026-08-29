package com.example.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_START_DOWNLOAD = "com.example.download.ACTION_START"
        const val ACTION_PAUSE_DOWNLOAD = "com.example.download.ACTION_PAUSE"
        const val ACTION_RESUME_DOWNLOAD = "com.example.download.ACTION_RESUME"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.download.ACTION_CANCEL"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_COOKIES = "extra_cookies"
        const val EXTRA_REFERER = "extra_referer"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FALLBACK_URL = "extra_fallback_url"

        fun startDownload(
            context: Context,
            url: String,
            fileName: String,
            userAgent: String? = null,
            cookies: String? = null,
            referer: String? = null,
            fallbackUrl: String? = null
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_USER_AGENT, userAgent)
                putExtra(EXTRA_COOKIES, cookies)
                putExtra(EXTRA_REFERER, referer)
                putExtra(EXTRA_FALLBACK_URL, fallbackUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "download.mp4"
                val userAgent = intent.getStringExtra(EXTRA_USER_AGENT)
                val cookies = intent.getStringExtra(EXTRA_COOKIES)
                val referer = intent.getStringExtra(EXTRA_REFERER)
                val fallbackUrl = intent.getStringExtra(EXTRA_FALLBACK_URL)

                MediaDownloader.downloadFileFromService(
                    context = this,
                    service = this,
                    url = url,
                    fileName = fileName,
                    coroutineScope = serviceScope,
                    userAgent = userAgent,
                    cookies = cookies,
                    referer = referer,
                    fallbackUrl = fallbackUrl
                )
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                MediaDownloader.togglePauseDownload(this, id)
            }
            ACTION_RESUME_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                MediaDownloader.togglePauseDownload(this, id)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                MediaDownloader.cancelDownload(id)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
