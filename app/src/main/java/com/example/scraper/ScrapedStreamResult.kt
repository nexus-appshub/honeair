package com.example.scraper

data class ScrapedStreamResult(
    val streamUrl: String,
    val cookie: String = "",
    val headers: Map<String, String> = emptyMap(),
    val referer: String = ""
)
