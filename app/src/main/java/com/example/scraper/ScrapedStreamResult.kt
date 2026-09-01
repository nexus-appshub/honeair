package com.example.scraper

data class SubtitleTrack(
    val url: String,
    val lang: String = "en",
    val label: String = "English",
    val default: Boolean = false
)

data class ScrapedStreamResult(
    val streamUrl: String,
    val cookie: String = "",
    val headers: Map<String, String> = emptyMap(),
    val referer: String = "",
    val subtitles: List<SubtitleTrack> = emptyList()
)
