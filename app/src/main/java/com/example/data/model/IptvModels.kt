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

// ১. স্পোর্টস ইভেন্ট রেসপন্স
data class SportsResponse(
    val ok: Boolean,
    val data: List<LiveMatch>
)

data class LiveMatch(
    val id: String,
    val title: String,
    val sportCategory: String,
    val tournament: String?,
    val teamA: Team,
    val teamB: Team,
    val bannerUrl: String?,
    val status: String, // live, upcoming, finished
    val startTime: String?,
    val badgeText: String?,
    val servers: List<StreamServer>?
)

data class Team(
    val name: String,
    val logo: String?,
    val score: String?
)

data class StreamServer(
    val name: String,
    val url: String, // ExoPlayer-এ সরাসরি প্লে করার জন্য .m3u8 লিঙ্ক
    val quality: String? = "FHD"
)

// ২. স্পোর্টস লাইভ টিভি চ্যানেল রেসপন্স
data class SportChannel(
    val id: String,
    val name: String,
    val url: String, // লাইভ টিভি স্ট্রিমিং লিঙ্ক
    val logo: String?,
    val group: String?
)

