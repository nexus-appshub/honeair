package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.network.SmartNetworkBoosterEngine
import com.example.notifications.LocalNotificationManager
import com.example.update.AppUpdateWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class MainApplication : Application(), ImageLoaderFactory {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Start network booster engine immediately at application level for ultra-fast response
            SmartNetworkBoosterEngine.startEngine(this)
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
                coil.Coil.imageLoader(this).memoryCache?.clear()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        val ultraOkHttpClient = OkHttpClient.Builder()
            .connectionPool(SmartNetworkBoosterEngine.sharedConnectionPool)
            .dispatcher(SmartNetworkBoosterEngine.sharedDispatcher)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host.lowercase()

                val reqBuilder = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .header("Cache-Control", "public, max-age=604800, max-stale=2592000") // 7 days fresh, 30 days stale cache

                when {
                    host.contains("anikoto") || host.contains("anipixcdn") -> {
                        reqBuilder.header("Referer", "https://anikoto.cz/")
                    }
                    host.contains("anilist") || host.contains("s4.anilist.co") -> {
                        reqBuilder.header("Referer", "https://anilist.co/")
                    }
                    host.contains("myanimelist") || host.contains("jikan") -> {
                        reqBuilder.header("Referer", "https://myanimelist.net/")
                    }
                    host.contains("tmdb.org") || host.contains("themoviedb.org") -> {
                        reqBuilder.header("Referer", "https://www.themoviedb.org/")
                    }
                    else -> {
                        reqBuilder.header("Referer", "https://www.google.com/")
                    }
                }

                chain.proceed(reqBuilder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(ultraOkHttpClient)
            // Memory cache (30% of app memory for ultra-fast instant scrolling)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            // Dedicated 300MB disk cache
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // 50% RAM savings with zero visual quality loss for posters
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(true)
            .crossfade(150) // Ultra fast 150ms crossfade for snappy responsive UI
            .build()
    }
}
