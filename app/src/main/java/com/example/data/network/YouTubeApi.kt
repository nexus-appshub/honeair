package com.example.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {
    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 1,
        @Query("type") type: String = "video",
        @Query("videoEmbeddable") videoEmbeddable: String = "true",
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

data class YouTubeSearchResponse(
    val items: List<YouTubeVideoItem>
)

data class YouTubeVideoItem(
    val id: YouTubeVideoId,
    val snippet: YouTubeVideoSnippet
)

data class YouTubeVideoId(
    val videoId: String
)

data class YouTubeVideoSnippet(
    val title: String,
    val description: String,
    val thumbnails: YouTubeThumbnails
)

data class YouTubeThumbnails(
    val default: YouTubeThumbnail,
    val medium: YouTubeThumbnail,
    val high: YouTubeThumbnail
)

data class YouTubeThumbnail(
    val url: String
)
