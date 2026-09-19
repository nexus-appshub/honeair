package com.example.scraper

import android.util.Log
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * High-performance Anime Metadata & Poster Engine with AniList GraphQL Standard:
 * 1. AniList GraphQL API (Industry-standard HD extraLarge covers + 16:9 bannerImages + UI colors)
 * 2. Secondary title normalization & fallback search
 * 3. Jikan API (MyAnimeList v4 large JPG/WebP) fallback
 * 4. High-performance concurrent memory cache for instant UI rendering across all screens
 */
object AnimePosterEngine {
    private const val TAG = "AnimePosterEngine"
    private const val ANILIST_GRAPHQL_ENDPOINT = "https://graphql.anilist.co"

    // High performance memory cache for instantly resolved anime posters/banners
    private val memoryCache = ConcurrentHashMap<String, AnimePosterResult>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Cleans an anime query/title by removing tags like [SUB], (DUB), etc. while preserving title specifics
     */
    fun cleanAnimeTitle(title: String): String {
        return title
            .removePrefix("anikoto_")
            .removePrefix("movie_")
            .removePrefix("series_")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""(?i)\b(uncensored|dub|sub|bd|fhd|hd)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Secondary fallback cleaning that strips season/part markers for broader search if specific query fails
     */
    fun stripSeasonOrPart(title: String): String {
        return title
            .replace(Regex("""(?i)\b(?:season|s)\s*\d+.*"""), "")
            .replace(Regex("""(?i)\bpart\s*\d+.*"""), "")
            .replace(Regex("""(?i)\bcour\s*\d+.*"""), "")
            .replace(Regex("""[:\-–—]"""), " ")
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

        // Tier 1: AniList GraphQL API with exact cleaned title
        var aniListResult = fetchFromAniList(clean)

        // If not found and title has colons, hyphens, or subtitle descriptions, try broader title
        if (aniListResult == null || aniListResult.posterUrl.isBlank()) {
            val stripped = stripSeasonOrPart(clean)
            if (stripped.isNotBlank() && !stripped.equals(clean, ignoreCase = true)) {
                aniListResult = fetchFromAniList(stripped)
            }
        }

        // If found from AniList, cache and return immediately
        if (aniListResult != null && aniListResult.posterUrl.isNotBlank()) {
            memoryCache[cacheKey] = aniListResult
            return@withContext aniListResult
        }

        // Tier 2: Jikan / MyAnimeList v4 REST API Fallback
        val jikanResult = fetchFromJikan(clean) ?: run {
            val stripped = stripSeasonOrPart(clean)
            if (stripped.isNotBlank() && !stripped.equals(clean, ignoreCase = true)) {
                fetchFromJikan(stripped)
            } else null
        }

        if (jikanResult != null && jikanResult.posterUrl.isNotBlank()) {
            memoryCache[cacheKey] = jikanResult
            return@withContext jikanResult
        }

        null
    }

    /**
     * 1. AniList GraphQL API Fetcher
     * Queries extraLarge and large cover images, bannerImage, color, score, description, year
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
                      userPreferred
                    }
                    coverImage {
                      extraLarge
                      large
                      medium
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
                .header("User-Agent", "HomeAirTV/4.7 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "AniList GraphQL error HTTP: ${response.code} for '$animeName'")
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
                    ?: coverObj?.optString("medium")
                    ?: ""
                val dominantColor = coverObj?.optString("color", "") ?: ""
                val banner = media.optString("bannerImage", "")
                val score = media.optInt("averageScore", 80)
                val year = media.optInt("seasonYear", 2024).toString()
                val rawDescription = media.optString("description", "")
                val cleanDescription = rawDescription.replace(Regex("<.*?>"), "").trim()

                val ratingFormatted = if (score > 0) String.format("%.1f", score / 10.0) else "8.4"

                if (extraLargePoster.isNotBlank()) {
                    return@withContext AnimePosterResult(
                        posterUrl = extraLargePoster,
                        bannerUrl = banner,
                        dominantColor = dominantColor,
                        titleEnglish = englishTitle,
                        titleRomaji = romajiTitle,
                        description = cleanDescription,
                        rating = ratingFormatted,
                        year = if (year != "0") year else "2024"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniList GraphQL fetch error for '$animeName': ${e.message}")
        }
        null
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
                .header("User-Agent", "HomeAirTV/4.7 (Android)")
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

                if (poster.isNotBlank()) {
                    return@withContext AnimePosterResult(
                        posterUrl = poster,
                        titleEnglish = title,
                        description = synopsis,
                        rating = String.format("%.1f", score),
                        year = if (year != "0") year else "2024"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jikan REST API fetch error for '$animeName': ${e.message}")
        }
        null
    }

    /**
     * Bulk fetch and enhance a list of Anime items with standardized AniList HD posters.
     * Concurrently fetches AniList GraphQL posters for all items in parallel.
     */
    suspend fun enhanceAnimeItems(items: List<AnikotoAnimeItem>): List<AnikotoAnimeItem> = withContext(Dispatchers.IO) {
        val jobs = items.map { item ->
            async {
                try {
                    // Query AniList GraphQL for high-resolution standardized poster & banner
                    val metadata = getAnimePosterAndBanner(item.title)
                    if (metadata != null && metadata.posterUrl.isNotBlank()) {
                        item.copy(
                            posterUrl = metadata.posterUrl,
                            description = item.description.ifBlank { metadata.description },
                            rating = if (item.rating.isNotBlank() && item.rating != "8.4") item.rating else metadata.rating,
                            releaseYear = item.releaseYear.ifBlank { metadata.year }
                        )
                    } else {
                        // Ensure existing poster is properly proxied so it doesn't fail
                        val safePoster = AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                        item.copy(posterUrl = safePoster)
                    }
                } catch (e: Exception) {
                    val safePoster = AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                    item.copy(posterUrl = safePoster)
                }
            }
        }
        jobs.awaitAll()
    }

    /**
     * Centralized utility to identify whether a media item is an anime based on id, category, type, and title keywords
     */
    fun isAnime(title: String, category: String? = null, type: String? = null, id: String? = null): Boolean {
        val t = title.lowercase()
        val cat = (category ?: "").lowercase()
        val tp = (type ?: "").lowercase()
        val idStr = (id ?: "").lowercase()

        if (idStr.startsWith("anikoto_") || idStr.startsWith("al_") || idStr.startsWith("mal_") || idStr.startsWith("ani_") || cat.contains("anime") || tp.equals("anime")) return true

        // Check for Japanese Kanji, Hiragana, Katakana unicode ranges
        if (title.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FAF' }) return true

        // Safe, specific anime titles and franchises that do not overlap with generic English words
        val safeKeywords = listOf(
            "naruto", "boruto", "one piece", "demon slayer", "attack on titan", "jujutsu kaisen",
            "my hero academia", "boku no hero", "konosuba", "solo leveling", "black clover", "frieren",
            "chainsaw man", "blue lock", "oshi no ko", "tokyo ghoul", "fairy tail", "death note",
            "dragon ball", "pokemon", "gundam", "rezero", "re:zero", "vinland saga", "neon genesis",
            "evangelion", "cowboy bebop", "clannad", "toradora", "spirited away", "howl's moving",
            "howls moving", "totoro", "mononoke", "dandadan", "bungo stray dogs", "haikyuu", "kuroko",
            "akame ga kill", "kill la kill", "assassination classroom", "sword art online", "psycho-pass",
            "gintama", "inuyasha", "yu yu hakusho", "sailor moon", "digimon", "cardcaptor", "fruits basket",
            "ouran"
        )
        
        val cleanTitle = t.trim()
        for (kw in safeKeywords) {
            if (kw.contains(" ")) {
                if (cleanTitle.contains(kw)) return true
            } else {
                val regex = "\\b${Regex.escape(kw)}\\b".toRegex()
                if (regex.containsMatchIn(cleanTitle)) return true
            }
        }
        return false
    }

    /**
     * Enhances a list of generic MediaItem objects with AniList high-resolution posters and descriptions
     */
    suspend fun enhanceMediaItems(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val jobs = items.map { item ->
            async {
                val isAnimeItem = isAnime(title = item.title, category = item.category, type = item.type, id = item.id)
                if (isAnimeItem) {
                    val metadata = getAnimePosterAndBanner(item.title)
                    if (metadata != null && metadata.posterUrl.isNotBlank()) {
                        item.copy(
                            imageUrl = metadata.posterUrl,
                            description = item.description.ifBlank { metadata.description },
                            rating = if (item.rating.isNotBlank() && item.rating != "8.4") item.rating else metadata.rating,
                            year = item.year.ifBlank { metadata.year }
                        )
                    } else {
                        item
                    }
                } else {
                    item
                }
            }
        }
        jobs.awaitAll()
    }

    /**
     * Directly fetch a page of trending / popular anime directly from AniList GraphQL
     */
    suspend fun fetchAniListMediaPage(
        page: Int = 1,
        perPage: Int = 25,
        format: String? = null,
        sortBy: String = "TRENDING_DESC"
    ): List<AnikotoAnimeItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnikotoAnimeItem>()
        try {
            val formatParam = if (!format.isNullOrBlank()) ", format: $format" else ""
            val query = """
                query (${'$'}page: Int, ${'$'}perPage: Int) {
                  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, sort: [$sortBy, POPULARITY_DESC]$formatParam) {
                      id
                      title {
                        english
                        romaji
                        userPreferred
                      }
                      coverImage {
                        extraLarge
                        large
                        color
                      }
                      bannerImage
                      averageScore
                      seasonYear
                      episodes
                      format
                      description(asHtml: false)
                    }
                  }
                }
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().apply {
                    put("page", page)
                    put("perPage", perPage)
                })
            }

            val requestBody = requestBodyJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(ANILIST_GRAPHQL_ENDPOINT)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "HomeAirTV/4.7 (Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(bodyStr)
                val data = json.optJSONObject("data") ?: return@withContext emptyList()
                val pageObj = data.optJSONObject("Page") ?: return@withContext emptyList()
                val mediaArr = pageObj.optJSONArray("media") ?: return@withContext emptyList()

                for (i in 0 until mediaArr.length()) {
                    val m = mediaArr.optJSONObject(i) ?: continue
                    val id = m.optInt("id", 0)
                    val titleObj = m.optJSONObject("title")
                    val englishTitle = titleObj?.optString("english", "") ?: ""
                    val romajiTitle = titleObj?.optString("romaji", "") ?: ""
                    val userTitle = titleObj?.optString("userPreferred", "") ?: ""
                    val displayTitle = englishTitle.ifBlank { userTitle.ifBlank { romajiTitle } }

                    val coverObj = m.optJSONObject("coverImage")
                    val poster = coverObj?.optString("extraLarge") ?: coverObj?.optString("large") ?: ""
                    val score = m.optInt("averageScore", 80)
                    val year = m.optInt("seasonYear", 2024).toString()
                    val episodes = m.optInt("episodes", 12)
                    val mediaFormat = m.optString("format", "TV")
                    val rawDesc = m.optString("description", "")
                    val cleanDesc = rawDesc.replace(Regex("<.*?>"), "").trim()

                    if (displayTitle.isNotBlank() && poster.isNotBlank()) {
                        results.add(
                            AnikotoAnimeItem(
                                id = "al_$id",
                                title = displayTitle,
                                posterUrl = poster,
                                watchUrl = "",
                                type = if (mediaFormat.equals("MOVIE", ignoreCase = true)) "Movie" else "TV",
                                subCount = "$episodes",
                                dubCount = "$episodes",
                                rating = String.format("%.1f", score / 10.0),
                                releaseYear = if (year != "0") year else "2024",
                                description = cleanDesc
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAniListMediaPage error: ${e.message}")
        }
        results
    }
}
