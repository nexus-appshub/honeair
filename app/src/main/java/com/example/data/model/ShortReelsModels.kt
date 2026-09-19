package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class AiringSource(val internalUrl: String) {
    AIRING_1("https://www.goodshort.com/dramas/playlets?openCategory=1"),
    AIRING_2("https://dashreels.com/")
}

data class ShortReel(
    val id: String,
    val title: String,
    val mediaUrl: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val likesCount: String? = null
)

data class AiringFeedState(
    val reels: List<ShortReel> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ShortReelsFeedResponse(
    @Json(name = "sessionId") val sessionId: String? = null,
    @Json(name = "items") val items: List<ShortReelRawItem>? = null,
    @Json(name = "newItems") val newItems: List<ShortReelRawItem>? = null,
    @Json(name = "hasMore") val hasMore: Boolean? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ShortReelRawItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "sourceUrl") val sourceUrl: String? = null,
    @Json(name = "mediaUrl") val mediaUrl: String? = null,
    @Json(name = "videoUrl") val videoUrl: String? = null,
    @Json(name = "streamUrl") val streamUrl: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "playUrl") val playUrl: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "thumbnailUrl") val thumbnailUrl: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "creator") val creator: String? = null,
    @Json(name = "likes") val likes: String? = null
) {
    fun toValidShortReel(): ShortReel? {
        val resolvedMediaUrl = (mediaUrl ?: videoUrl ?: streamUrl ?: playUrl ?: url)?.trim().orEmpty()
        if (resolvedMediaUrl.isBlank()) return null
        
        // Reject .ts media segment files
        val cleanUrlPath = resolvedMediaUrl.substringBefore("?").lowercase()
        if (cleanUrlPath.endsWith(".ts")) return null

        // Must be a valid supported stream format (.m3u8, .mpd, .mp4, .webm or general valid http video stream)
        val validUrl = resolvedMediaUrl.startsWith("http://") || resolvedMediaUrl.startsWith("https://")
        if (!validUrl) return null

        val resolvedThumbnail = (thumbnail ?: thumbnailUrl ?: poster ?: cover)?.trim()
        val resolvedId = id?.ifBlank { null } ?: resolvedMediaUrl.hashCode().toString()

        var formattedTitle = title?.trim()
        if (formattedTitle.isNullOrBlank() && !sourceUrl.isNullOrBlank()) {
            try {
                // e.g. https://www.goodshort.com/episode/blood-and-bones-of-the-disowned-daughter-31001113972/003-14268810
                val pathSegments = sourceUrl.trim().split("/").filter { it.isNotBlank() }
                val dramaSegment = pathSegments.find { it.contains("-") && !it.startsWith("00") && !it.startsWith("ep") }
                val epSegment = pathSegments.find { it.matches(Regex("^[0-9]+-[0-9]+$")) || it.startsWith("ep") }
                
                val dramaName = dramaSegment?.replace(Regex("-\\d+$"), "")?.replace("-", " ")?.split(" ")?.joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

                val epNumber = epSegment?.substringBefore("-")?.toIntOrNull()?.let { "Episode $it" } ?: ""

                formattedTitle = when {
                    !dramaName.isNullOrBlank() && epNumber.isNotBlank() -> "$dramaName • $epNumber"
                    !dramaName.isNullOrBlank() -> dramaName
                    else -> "Drama Reel"
                }
            } catch (_: Exception) {
                formattedTitle = "Drama Reel"
            }
        }

        val resolvedTitle = formattedTitle?.ifBlank { null } ?: caption?.trim()?.ifBlank { null } ?: description?.trim()?.ifBlank { null } ?: "Drama Reel"
        val resolvedAuthor = (author ?: creator)?.trim()
        val resolvedDesc = (description ?: caption)?.trim()

        return ShortReel(
            id = resolvedId,
            title = resolvedTitle,
            mediaUrl = resolvedMediaUrl,
            thumbnailUrl = resolvedThumbnail,
            description = resolvedDesc,
            author = resolvedAuthor,
            likesCount = likes
        )
    }
}
