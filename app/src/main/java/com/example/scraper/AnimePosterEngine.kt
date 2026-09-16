package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AnimePosterResult(
    val posterUrl: String,
    val bannerUrl: String = "",
    val dominantColor: String = "",
    val titleEnglish: String = "",
    val titleRomaji: String = "",
    val description: String = "",
    val rating: String = "",
    val year: String = ""
)

/**
 * High-performance Anime Metadata & Poster Engine with Tiered Fallback:
 * 1. AniList GraphQL API (Industry-standard HD extraLarge covers + 16:9 bannerImages + UI colors)
 * 2. Jikan API (MyAnimeList v4 large JPG/WebP)
 * 3. In-memory caching for zero redundant network calls and instant UI rendering
 */
object AnimePosterEngine {
    private const val TAG = "AnimePosterEngine"
    private const val ANILIST_GRAPHQL_ENDPOINT = "https://graphql.anilist.co"

    // High performance memory cache for instantly resolved anime posters/banners
    private val memoryCache = ConcurrentHashMap<String, AnimePosterResult>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Cleans an anime query/title by removing season, dub/sub markers, and episode tags
     */
    fun cleanAnimeTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\b(season\s*\d+|part\s*\d+|cour\s*\d+|s\d+)\b"""), "")
            .replace(Regex("""(?i)\b(dub|sub|uncensored|tv|movie|special|ova|ona)\b"""), "")
            .replace(Regex("""[\[\]\(\)\{\}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Resolves high-resolution anime poster and banner by title or query.
     */
    suspend fun getAnimePosterAndBanner(title: String): AnimePosterResult? = withContext(Dispatchers.IO) {
        val clean = cleanAnimeTitle(title)
        if (clean.isBlank()) return@withContext null

        val cacheKey = clean.lowercase()
        memoryCache[cacheKey]?.let { return@withContext it }

        // Tier 1: AniList GraphQL API (ExtraLarge HD Poster + Banner Image)
        val aniListResult = fetchFromAniList(clean)
        if (aniListResult != null && aniListResult.posterUrl.isNotBlank()) {
            memoryCache[cacheKey] = aniListResult
            return@withContext aniListResult
        }

        // Tier 2: Jikan / MyAnimeList v4 REST API
        val jikanResult = fetchFromJikan(clean)
        if (jikanResult != null && jikanResult.posterUrl.isNotBlank()) {
            memoryCache[cacheKey] = jikanResult
            return@withContext jikanResult
        }

        null
    }

    /**
     * 1. AniList GraphQL API Fetcher
     */
    private suspend fun fetchFromAniList(animeName: String): AnimePosterResult? = withContext(Dispatchers.IO) {
        try {
            val graphqlQuery = """
                query (${'$'}search: String) {
                  Media (search: ${'$'}search, type: ANIME) {
                    id
                    title {
                      english
                      romaji
                    }
                    coverImage {
                      extraLarge
                      large
                      color
                    }
                    bannerImage
                    averageScore
                    seasonYear
                    description(asHtml: false)
                  }
                }
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("query", graphqlQuery)
                put("variables", JSONObject().apply {
                    put("search", animeName)
                })
            }

            val requestBody = requestBodyJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(ANILIST_GRAPHQL_ENDPOINT)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "HomeAirTV/4.6.9 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "AniList GraphQL error HTTP: ${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val data = json.optJSONObject("data") ?: return@withContext null
                val media = data.optJSONObject("Media") ?: return@withContext null

                val titleObj = media.optJSONObject("title")
                val englishTitle = titleObj?.optString("english", "") ?: ""
                val romajiTitle = titleObj?.optString("romaji", "") ?: ""

                val coverObj = media.optJSONObject("coverImage")
                val extraLargePoster = coverObj?.optString("extraLarge")
                    ?: coverObj?.optString("large")
                    ?: ""
                val dominantColor = coverObj?.optString("color", "") ?: ""
                val banner = media.optString("bannerImage", "")
                val score = media.optInt("averageScore", 80)
                val year = media.optInt("seasonYear", 2024).toString()
                val description = media.optString("description", "")

                val ratingFormatted = if (score > 0) String.format("%.1f", score / 10.0) else "8.4"

                return@withContext AnimePosterResult(
                    posterUrl = extraLargePoster,
                    bannerUrl = banner,
                    dominantColor = dominantColor,
                    titleEnglish = englishTitle,
                    titleRomaji = romajiTitle,
                    description = description,
                    rating = ratingFormatted,
                    year = if (year != "0") year else "2024"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniList GraphQL fetch error for '$animeName': ${e.message}")
            null
        }
    }

    /**
     * 2. Jikan (MyAnimeList v4) REST API Fetcher
     */
    private suspend fun fetchFromJikan(animeName: String): AnimePosterResult? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(animeName, "UTF-8")
            val url = "https://api.jikan.moe/v4/anime?q=$encodedQuery&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HomeAirTV/4.6.9 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val dataArr = json.optJSONArray("data") ?: return@withContext null
                if (dataArr.length() == 0) return@withContext null

                val firstAnime = dataArr.optJSONObject(0) ?: return@withContext null
                val images = firstAnime.optJSONObject("images")
                val jpg = images?.optJSONObject("jpg")
                val webp = images?.optJSONObject("webp")

                val poster = jpg?.optString("large_image_url")
                    ?: jpg?.optString("image_url")
                    ?: webp?.optString("large_image_url")
                    ?: webp?.optString("image_url")
                    ?: ""

                val title = firstAnime.optString("title_english").ifBlank { firstAnime.optString("title", "") }
                val score = firstAnime.optDouble("score", 8.2)
                val year = firstAnime.optInt("year", 2024).toString()
                val synopsis = firstAnime.optString("synopsis", "")

                return@withContext AnimePosterResult(
                    posterUrl = poster,
                    titleEnglish = title,
                    description = synopsis,
                    rating = String.format("%.1f", score),
                    year = if (year != "0") year else "2024"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jikan REST API fetch error for '$animeName': ${e.message}")
            null
        }
    }

    /**
     * Bulk fetch and enhance a list of Anime items with AniList / Jikan posters
     */
    suspend fun enhanceAnimeItems(items: List<AnikotoAnimeItem>): List<AnikotoAnimeItem> = withContext(Dispatchers.IO) {
        items.map { item ->
            val needsBetterPoster = item.posterUrl.isBlank() ||
                    item.posterUrl.contains("placeholder") ||
                    item.posterUrl.endsWith("null") ||
                    item.posterUrl.endsWith("undefined")

            if (needsBetterPoster || !item.posterUrl.startsWith("http")) {
                val metadata = getAnimePosterAndBanner(item.title)
                if (metadata != null && metadata.posterUrl.isNotBlank()) {
                    item.copy(
                        posterUrl = metadata.posterUrl,
                        description = item.description.ifBlank { metadata.description },
                        rating = item.rating.ifBlank { metadata.rating },
                        releaseYear = item.releaseYear.ifBlank { metadata.year }
                    )
                } else {
                    item
                }
            } else {
                item
            }
        }
    }
}
