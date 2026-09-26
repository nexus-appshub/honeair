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
    val tvgId: String = "",
    val isPremium: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

// ১. স্পোর্টস ইভেন্ট রেসপন্স
data class SportsResponse(
    val ok: Boolean = true,
    val data: List<LiveMatch> = emptyList(),
    val events: List<LiveMatch>? = null,
    val sports: List<LiveMatch>? = null,
    val matches: List<LiveMatch>? = null,
    val count: Int? = null,
    val timestamp: String? = null
)

// LiveMatch / SportsEvent Root Model
data class LiveMatch(
    val id: String,
    val title: String,
    val sportCategory: String = "Football",
    val tournament: String? = null,
    val teamA: Team = Team("Team A", null, null),
    val teamB: Team = Team("Team B", null, null),
    val bannerUrl: String? = null,
    val status: String = "live", // "live" | "upcoming" | "ended" | "finished"
    val startTime: String? = null,
    val badgeText: String? = null,
    val description: String? = null,
    val isPinned: Boolean = false,
    val isActive: Boolean = true,
    val viewersCount: Long = 0L,
    val gameState: GameStateInfo? = null,
    val importanceScore: Double = 0.0,
    val servers: List<StreamServer>? = emptyList()
)

// Alias for SportsEvent
typealias SportsEvent = LiveMatch

data class Team(
    val name: String = "Team",
    val logo: String? = null,
    val score: String? = null
)

typealias TeamInfo = Team

data class GameStateInfo(
    val minute: String? = null,      // Football: "64"
    val period: String? = null,      // Football: "1st Half" / "2nd Half"
    val overs: String? = null,       // Cricket: "45.2"
    val inning: String? = null,      // Cricket: "1" or "2"
    val statusText: String? = null   // "Drinks Break", "Half Time", "Stumps"
)

data class StreamServer(
    val id: String = "",
    val name: String,
    val url: String, // ExoPlayer direct .m3u8, .mp4, or web stream URL
    val quality: String? = "1080p",
    val type: String? = "hls",       // "hls" | "mp4" | "embed"
    val isDirect: Boolean = false,
    val referer: String? = null,
    val headers: Map<String, String>? = null
)

typealias SportsStreamServer = StreamServer

// ২. স্পোর্টস লাইভ টিভি চ্যানেল রেসপন্স
data class SportChannel(
    val id: String,
    val name: String,
    val url: String, // লাইভ টিভি স্ট্রিমিং লিঙ্ক
    val logo: String?,
    val group: String?,
    val isPremium: Boolean = false
)

data class FloatingPlayerInstance(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val isChannel: Boolean = true,
    val channel: IptvChannel? = null,
    val mediaItem: MediaItem? = null,
    val streamUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val season: Int = 1,
    val episode: Int = 1,
    val isMuted: Boolean = false,
    val isPlaying: Boolean = true,
    val initialX: Float = 0f,
    val initialY: Float = 0f
)
