package com.example.data.model

data class IptvPlaylist(
    val name: String,
    val url: String,
    val group: String = "All",
    val logo: String = ""
)

data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val tvgId: String = ""
)
