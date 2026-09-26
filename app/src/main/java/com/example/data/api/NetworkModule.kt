package com.example.data.api

import com.example.network.SmartNetworkBoosterEngine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(SmartNetworkBoosterEngine.sharedDispatcher)
            .connectionPool(SmartNetworkBoosterEngine.sharedConnectionPool)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
