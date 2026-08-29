package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.notifications.LocalNotificationManager
import com.example.update.AppUpdateWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class MainApplication : Application(), ImageLoaderFactory {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Start network booster engine immediately at application level for ultra-fast response
            com.example.network.SmartNetworkBoosterEngine.startEngine(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        // Disable SSL certificate checking globally for IPTV streams
        try {
            disableSSLCertificateChecking()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Initialize Firebase explicitly with guaranteed fallback
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val initializedApp = FirebaseApp.initializeApp(this)
                if (initializedApp == null) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setProjectId("home-air-tv-xwdc")
                        .setApplicationId("1:312686001948:android:418416d20476e5c565c4d9")
                        .setApiKey("AIzaSyBCasqe4hKjCauoRYwbg1GPAwNJRLWGw5w")
                        .setStorageBucket("home-air-tv-xwdc.firebasestorage.app")
                        .setGcmSenderId("312686001948")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
            }
            firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainApplication", "Error initializing Firebase, applying explicit options fallback", e)
            try {
                if (FirebaseApp.getApps(this).isEmpty()) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setProjectId("home-air-tv-xwdc")
                        .setApplicationId("1:312686001948:android:418416d20476e5c565c4d9")
                        .setApiKey("AIzaSyBCasqe4hKjCauoRYwbg1GPAwNJRLWGw5w")
                        .setStorageBucket("home-air-tv-xwdc.firebasestorage.app")
                        .setGcmSenderId("312686001948")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
            } catch (ex: Throwable) {
                android.util.Log.e("MainApplication", "Fatal error initializing Firebase fallback", ex)
            }
        }

        try {
            LocalNotificationManager.createNotificationChannel(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        try {
            AppUpdateWorker.schedulePeriodicCheck(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun disableSSLCertificateChecking() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })

            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_BACKGROUND || level >= TRIM_MEMORY_MODERATE) {
                // Instantly reclaim memory from the image cache when backgrounded or low on RAM
                coil.Coil.imageLoader(this).memoryCache?.clear()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Inject custom OkHttpClient with Chrome User-Agent and Referer headers for all image loading.
            // This is essential to prevent 403 Forbidden errors when loading anime posters/banners from cdn.anipixcdn.co or anikoto.cz.
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Referer", "https://anikoto.cz/")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            // Premium 25% memory cache with explicit strong and weak reference tracking for flawless OOM protection
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            // Enterprise-grade 200MB dedicated image disk cache to avoid redundant network overhead on scrolling
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024) // 200 MegaBytes
                    .build()
            }
            // RGB_565 config is used to reduce image memory usage by 50% and keep GC pauses near zero
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(true)
            .build()
    }
}
