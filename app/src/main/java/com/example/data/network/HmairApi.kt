package com.example.data.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET

@JsonClass(generateAdapter = true)
data class HmairChannel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = ""
)

@JsonClass(generateAdapter = true)
data class HmairApiResponse(
    val channels: List<HmairChannel>
)

interface HmairApi {
    @GET("api/channels")
    suspend fun getChannels(): HmairApiResponse
}
