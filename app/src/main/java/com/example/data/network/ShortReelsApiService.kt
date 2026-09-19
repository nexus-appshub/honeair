package com.example.data.network

import com.example.data.model.ShortReelsFeedResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ShortReelsApiService {
    @GET("v1/feed")
    suspend fun getFeedByUrl(
        @Query("url") url: String,
        @Query("limit") limit: Int = 10
    ): Response<ShortReelsFeedResponse>

    @GET("v1/feed")
    suspend fun getFeedBySessionId(
        @Query("sessionId") sessionId: String,
        @Query("limit") limit: Int = 10
    ): Response<ShortReelsFeedResponse>
}

object ShortReelsApiClient {
    private const val BASE_URL = "https://shortreels-scraper-1.onrender.com/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "HomeAirTV-Android/4.7")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    val apiService: ShortReelsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ShortReelsApiService::class.java)
    }
}
