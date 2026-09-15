package com.example.data.model

data class MediaItem(
    val id: String,
    val title: String,
    val category: String, // "Anime", "Movies", "Series & TV Shows", "Short TV", "K-Dramas", "Hindi Dubbed", "Anime Shorts"
    val imageUrl: String,
    val rating: String = "4.8",
    val year: String = "2024",
    val description: String = "",
    val streamUrl: String = "",
    val episodes: String = "",
    val isStreamable: Boolean = true,
    val imdbId: String? = null,
    val type: String = "movie", // "movie" or "series"
    val isPremium: Boolean = false
)
