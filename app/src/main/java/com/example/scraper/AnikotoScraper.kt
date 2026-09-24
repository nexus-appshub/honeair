package com.example.scraper

import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.example.network.DiagnosticLoggingInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AnikotoAnimeItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val watchUrl: String,
    val type: String = "TV",
    val episodesInfo: String = "",
    val rating: String = "",
    val subCount: String = "",
    val dubCount: String = "",
    val description: String = "",
    val releaseYear: String = ""
)

data class AnikotoEpisode(
    val id: String,
    val number: Int,
    val title: String,
    val dataIdsToken: String = ""
)

data class AnikotoSeason(
    val number: Int,
    val title: String,
    val watchUrl: String,
    val posterUrl: String = "",
    val subCount: String = "",
    val dubCount: String = ""
)

data class AnikotoDetails(
    val title: String,
    val posterUrl: String,
    val description: String,
    val rating: String,
    val releaseYear: String,
    val totalSeasons: Int,
    val totalEpisodes: Int
)

data class AnikotoServer(
    val id: String,
    val linkId: String,
    val name: String,
    val type: String, // "sub", "dub", "dl"
    val streamUrl: String = "",
    val rawUrl: String = "",
    val referer: String = "",
    val tracks: List<SubtitleTrack> = emptyList()
)

data class AnikotoServerGroup(
    val subServers: List<AnikotoServer> = emptyList(),
    val dubServers: List<AnikotoServer> = emptyList(),
    val watchUrl: String = "",
    val episodeNum: Int = 1
)

data class AnikotoSubtitle(
    val url: String,
    val lang: String,
    val label: String,
    val default: Boolean = false
)

data class AnikotoStreamResult(
    val streamUrl: String,
    val headers: Map<String, String>,
    val referer: String,
    val subtitles: List<AnikotoSubtitle> = emptyList(),
    val serverName: String = "",
    val isM3u8: Boolean = true
)

object AnikotoScraper {
    private const val TAG = "AnikotoScraper"
    const val API_BASE_URL = "https://media.hmair.xyz"
    const val ANIKOTO_ORIGIN = "https://anikoto.cz"
    const val MEGAPLAY_ORIGIN = "https://megaplay.buzz"
    const val MAPPER_ORIGIN = "https://mapper.nekostream.site"
    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(DiagnosticLoggingInterceptor(tag = "AnimeApi-Diagnostic", enforceBypassHeaders = true))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun fetchJson(url: String, customHeaders: Map<String, String> = emptyMap()): JSONObject? {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")

            for ((k, v) in customHeaders) {
                reqBuilder.header(k, v)
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: return null
            if (body.startsWith("{") || body.startsWith("[")) {
                if (body.startsWith("[")) {
                    JSONObject().put("data", JSONArray(body))
                } else {
                    JSONObject(body)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchJson error for $url: ${e.message}")
            null
        }
    }

    private fun postJson(url: String, formParams: Map<String, String> = emptyMap(), customHeaders: Map<String, String> = emptyMap()): JSONObject? {
        return try {
            val formBuilder = FormBody.Builder()
            for ((k, v) in formParams) {
                formBuilder.add(k, v)
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")

            for ((k, v) in customHeaders) {
                reqBuilder.header(k, v)
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: return null
            if (body.startsWith("{")) {
                JSONObject(body)
            } else {
                JSONObject().put("raw", body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "postJson error for $url: ${e.message}")
            null
        }
    }

    private fun makeAbsoluteUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed == "/" || trimmed.equals("null", ignoreCase = true)) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.startsWith("/")) {
            "$API_BASE_URL$trimmed"
        } else {
            "$API_BASE_URL/$trimmed"
        }
    }

    fun sanitizePosterUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) return ""
        if (trimmed.contains("anilist.co") || trimmed.contains("myanimelist.net") || trimmed.contains("tmdb.org")) {
            return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        }
        if (trimmed.startsWith("/api/proxy") || trimmed.startsWith("/api/")) {
            return "$API_BASE_URL$trimmed"
        }
        val target = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> "https://anikoto.cz$trimmed"
            else -> "https://anikoto.cz/$trimmed"
        }
        return if (target.contains("anipixcdn.co") || target.contains("anikoto.cz")) {
            "$API_BASE_URL/api/proxy?url=${URLEncoder.encode(target, "UTF-8")}&referer=https%3A%2F%2Fanikoto.cz%2F"
        } else {
            target
        }
    }

    /**
     * Direct scraping of Anikoto filter page (https://anikoto.cz/filter) using Jsoup
     */
    private fun scrapeAnikotoFilterPage(
        keyword: String? = null,
        genre: String? = null,
        page: Int = 1
    ): List<AnikotoAnimeItem> {
        val list = mutableListOf<AnikotoAnimeItem>()
        try {
            val queryParams = mutableListOf<String>()
            if (!keyword.isNullOrBlank()) {
                queryParams.add("keyword=${URLEncoder.encode(keyword.trim(), "UTF-8")}")
            }
            if (!genre.isNullOrBlank()) {
                queryParams.add("genre=${URLEncoder.encode(genre.trim(), "UTF-8")}")
            }
            if (page > 1) {
                queryParams.add("page=$page")
            }
            val qStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
            val targetUrl = "https://anikoto.cz/filter$qStr"
            Log.d(TAG, "Direct Anikoto Filter Scraping: $targetUrl")

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "https://anikoto.cz/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return list
                val html = response.body?.string() ?: return list
                val doc = org.jsoup.Jsoup.parse(html)
                val cards = doc.select(".flw-item, .ani.poster.tip, .film_list-wrap .flw-item")
                for (card in cards) {
                    val titleEl = card.selectFirst(".film-name a, .dynamic-name, h3.film-name a, .film-detail .film-name")
                    val title = titleEl?.text()?.trim() ?: card.selectFirst("a")?.attr("title")?.trim() ?: ""
                    if (title.isBlank()) continue

                    val link = titleEl?.attr("href") ?: card.selectFirst("a")?.attr("href") ?: ""
                    val fullWatchUrl = if (link.startsWith("http")) link else "https://anikoto.cz${if (link.startsWith("/")) "" else "/"}$link"

                    val imgEl = card.selectFirst("img.film-poster-img, .film-poster img, img")
                    val posterRaw = imgEl?.attr("data-src")?.ifBlank { imgEl.attr("src") } ?: ""
                    val poster = sanitizePosterUrl(posterRaw)

                    val dataId = card.attr("data-id").ifBlank {
                        card.selectFirst(".film-poster")?.attr("data-id") ?: link.substringAfterLast("/").substringBefore("?")
                    }

                    val subCount = card.selectFirst(".tick-sub, .tick-item.tick-sub")?.text()?.trim() ?: ""
                    val dubCount = card.selectFirst(".tick-dub, .tick-item.tick-dub")?.text()?.trim() ?: ""
                    val rating = card.selectFirst(".tick-rate, .rating, .fdi-item:contains(★)")?.text()?.replace("★", "")?.trim() ?: "8.5"
                    val itemType = card.selectFirst(".fdi-item:not(:contains(★)), .tick-item")?.text()?.trim() ?: "TV"

                    list.add(
                        AnikotoAnimeItem(
                            id = if (dataId.isNotBlank()) dataId else "ani_${list.size}",
                            title = title,
                            posterUrl = poster,
                            watchUrl = fullWatchUrl,
                            type = itemType,
                            subCount = subCount,
                            dubCount = dubCount,
                            rating = rating,
                            releaseYear = "2024",
                            description = ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "scrapeAnikotoFilterPage error: ${e.message}")
        }
        return list
    }

    /**
     * Direct scraping of Anikoto Episode list (https://anikoto.cz/ajax/v2/episode/list/[ANIME_ID])
     */
    private fun scrapeAnikotoEpisodesDirect(animeIdOrWatchUrl: String): List<AnikotoEpisode> {
        val list = mutableListOf<AnikotoEpisode>()
        try {
            val cleanId = if (animeIdOrWatchUrl.contains("/watch/")) {
                animeIdOrWatchUrl.substringAfterLast("/watch/").substringBefore("?").substringAfterLast("-")
            } else if (animeIdOrWatchUrl.all { it.isDigit() }) {
                animeIdOrWatchUrl
            } else {
                animeIdOrWatchUrl.substringAfterLast("-")
            }

            if (cleanId.isNotBlank()) {
                val ajaxUrl = "https://anikoto.cz/ajax/v2/episode/list/$cleanId"
                val req = Request.Builder()
                    .url(ajaxUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "https://anikoto.cz/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val html = if (body.startsWith("{")) {
                            JSONObject(body).optString("html", "")
                        } else {
                            body
                        }
                        if (html.isNotBlank()) {
                            val doc = org.jsoup.Jsoup.parse(html)
                            val epElements = doc.select(".ep-item, a[data-id], .episodes-ul a, .ssl-item")
                            for (el in epElements) {
                                val epId = el.attr("data-id").ifBlank { el.attr("data-number") }
                                val epNum = el.attr("data-number").toIntOrNull()
                                    ?: el.text().filter { it.isDigit() }.toIntOrNull()
                                    ?: (list.size + 1)
                                val epTitle = el.attr("title").ifBlank { el.text().trim() }
                                list.add(
                                    AnikotoEpisode(
                                        id = epId.ifBlank { "$epNum" },
                                        number = epNum,
                                        title = if (epTitle.isBlank()) "Episode $epNum" else epTitle,
                                        dataIdsToken = epId
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "scrapeAnikotoEpisodesDirect error: ${e.message}")
        }
        return list
    }

    /**
     * 1. Search & Discovery: Filter / Search / Browse Anime from https://media.hmair.xyz/api/anikoto/search
     * with multi-tier fail-safe fallbacks (Jikan / MyAnimeList & TMDB Anime APIs) for HD posters
     */
    suspend fun searchOrFilterAnime(
        keyword: String? = null,
        genre: String? = null,
        season: String? = null,
        year: String? = null,
        type: String? = null,
        status: String? = null,
        language: String? = null,
        rating: String? = null,
        sortBy: String? = null,
        page: Int = 1
    ): List<AnikotoAnimeItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnikotoAnimeItem>()
        
        // Tier 1: Anikoto Central Server API
        try {
            val queryParams = mutableListOf<String>()
            if (!keyword.isNullOrBlank()) {
                queryParams.add("keyword=${URLEncoder.encode(keyword.trim(), "UTF-8")}")
            }
            if (!genre.isNullOrBlank()) {
                queryParams.add("genre=${URLEncoder.encode(genre.trim(), "UTF-8")}")
            }
            if (!type.isNullOrBlank()) {
                queryParams.add("type=${URLEncoder.encode(type.trim(), "UTF-8")}")
            }
            if (!language.isNullOrBlank()) {
                queryParams.add("language=${URLEncoder.encode(language.trim(), "UTF-8")}")
            }
            if (!sortBy.isNullOrBlank()) {
                queryParams.add("sort_by=${URLEncoder.encode(sortBy.trim(), "UTF-8")}")
            }
            if (page > 1) {
                queryParams.add("page=$page")
            }

            val queryString = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
            val url = "$API_BASE_URL/api/anikoto/search$queryString"
            Log.d(TAG, "Calling Anime Search API: $url")

            val json = fetchJson(url)
            if (json != null) {
                val animeArray = json.optJSONArray("anime") ?: json.optJSONArray("results") ?: JSONArray()
                for (i in 0 until animeArray.length()) {
                    val itemObj = animeArray.optJSONObject(i) ?: continue
                    val id = itemObj.optString("id", "")
                    val title = itemObj.optString("title", "")
                    val link = itemObj.optString("link", "")
                    val rawPoster = itemObj.optString("poster", "")
                    val poster = sanitizePosterUrl(rawPoster)
                    val subCount = itemObj.optString("subCount", "")
                    val dubCount = itemObj.optString("dubCount", "")
                    val itemType = itemObj.optString("type", type ?: "TV")
                    val ratingStr = itemObj.optString("rating", "")
                    val releaseYear = itemObj.optString("releaseYear", "")
                    val desc = itemObj.optString("description", "")

                    if (title.isNotBlank()) {
                        val fullWatchUrl = if (link.isNotBlank()) link else "$API_BASE_URL/watch/$id"
                        results.add(
                            AnikotoAnimeItem(
                                id = id.ifBlank { "anime_$i" },
                                title = title,
                                posterUrl = poster,
                                watchUrl = fullWatchUrl,
                                type = itemType,
                                subCount = subCount,
                                dubCount = dubCount,
                                rating = ratingStr,
                                releaseYear = releaseYear,
                                description = desc
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchOrFilterAnime Tier 1 error: ${e.message}")
        }

        // Tier 2 Fallback: Direct Anikoto Filter Page Scraping
        if (results.isEmpty()) {
            val directList = scrapeAnikotoFilterPage(keyword = keyword, genre = genre, page = page)
            if (directList.isNotEmpty()) {
                results.addAll(directList)
            }
        }

        // Tier 3 Fallback: AniList GraphQL Standard API
        if (results.isEmpty()) {
            try {
                val aniListFormat = if (type?.lowercase() == "movie") "MOVIE" else if (type?.lowercase() == "tv") "TV" else null
                val aniListItems = AnimePosterEngine.fetchAniListMediaPage(page = page, perPage = 25, format = aniListFormat)
                if (aniListItems.isNotEmpty()) {
                    results.addAll(aniListItems)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AniList fallback error: ${e.message}")
            }
        }

        // Tier 4 Fallback: Jikan / MyAnimeList Anime REST API
        if (results.isEmpty()) {
            try {
                val jikanUrl = if (!keyword.isNullOrBlank()) {
                    "https://api.jikan.moe/v4/anime?q=${URLEncoder.encode(keyword.trim(), "UTF-8")}&limit=25&page=$page"
                } else {
                    val jikanType = if (type?.lowercase() == "movie") "movie" else "tv"
                    "https://api.jikan.moe/v4/top/anime?type=$jikanType&page=$page&limit=25"
                }
                Log.d(TAG, "Calling Jikan Anime Fallback API: $jikanUrl")
                val jikanJson = fetchJson(jikanUrl)
                if (jikanJson != null) {
                    val dataArr = jikanJson.optJSONArray("data")
                    if (dataArr != null && dataArr.length() > 0) {
                        for (i in 0 until dataArr.length()) {
                            val aObj = dataArr.optJSONObject(i) ?: continue
                            val malId = aObj.optString("mal_id", "")
                            val title = aObj.optString("title_english").ifBlank { aObj.optString("title", "") }
                            val images = aObj.optJSONObject("images")
                            val jpg = images?.optJSONObject("jpg")
                            val webp = images?.optJSONObject("webp")
                            val poster = jpg?.optString("large_image_url") 
                                ?: jpg?.optString("image_url") 
                                ?: webp?.optString("large_image_url") 
                                ?: ""
                            val score = aObj.optDouble("score", 8.4)
                            val yearVal = aObj.optInt("year", 2024).toString()
                            val synopsis = aObj.optString("synopsis", "")
                            val epCount = aObj.optInt("episodes", 12)
                            val aType = aObj.optString("type", type ?: "TV")

                            if (title.isNotBlank()) {
                                results.add(
                                    AnikotoAnimeItem(
                                        id = "mal_$malId",
                                        title = title,
                                        posterUrl = poster,
                                        watchUrl = "$API_BASE_URL/watch/anime-$malId",
                                        type = aType,
                                        subCount = "$epCount",
                                        dubCount = "$epCount",
                                        rating = String.format("%.1f", score),
                                        releaseYear = if (yearVal != "0") yearVal else "2024",
                                        description = synopsis
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Jikan fallback error: ${e.message}")
            }
        }

        AnimePosterEngine.enhanceAnimeItems(results)
    }

    suspend fun getLiveSuggestions(keyword: String): List<AnikotoAnimeItem> = withContext(Dispatchers.IO) {
        searchOrFilterAnime(keyword = keyword)
    }

    /**
     * 2. Fetch All Seasons & Franchise Parts
     */
    suspend fun fetchSeasons(watchUrlOrSlug: String, animeTitle: String? = null, episode: Int = 1): List<AnikotoSeason> = withContext(Dispatchers.IO) {
        val rawSeasonsList = mutableListOf<AnikotoSeason>()
        val seenWatchUrls = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        try {
            val queryParams = mutableListOf<String>()
            if (watchUrlOrSlug.startsWith("http") || watchUrlOrSlug.contains("/watch/")) {
                queryParams.add("url=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            } else if (!animeTitle.isNullOrBlank()) {
                queryParams.add("keyword=${URLEncoder.encode(animeTitle.trim(), "UTF-8")}")
            } else {
                queryParams.add("keyword=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            }
            queryParams.add("ep=$episode")

            val url = "$API_BASE_URL/api/stream/get?${queryParams.joinToString("&")}"
            Log.d(TAG, "Fetching Seasons from API: $url")
            val json = fetchJson(url)

            if (json != null) {
                val seasonsArray = json.optJSONArray("seasons")
                if (seasonsArray != null && seasonsArray.length() > 0) {
                    for (i in 0 until seasonsArray.length()) {
                        val sObj = seasonsArray.optJSONObject(i) ?: continue
                        val title = sObj.optString("title", "Season ${i + 1}").trim()
                        val link = sObj.optString("link", "").trim()
                        val poster = sObj.optString("poster", "").trim()
                        val subCount = sObj.optString("subCount", "")
                        val dubCount = sObj.optString("dubCount", "")
                        val finalWatch = link.ifBlank { watchUrlOrSlug }

                        val cleanT = title.lowercase().trim()
                        val normalizedWatch = finalWatch.removeSuffix("/").lowercase()

                        if (!seenWatchUrls.contains(normalizedWatch) && !seenTitles.contains(cleanT)) {
                            seenWatchUrls.add(normalizedWatch)
                            seenTitles.add(cleanT)
                            rawSeasonsList.add(
                                AnikotoSeason(
                                    number = i + 1,
                                    title = title,
                                    watchUrl = finalWatch,
                                    posterUrl = poster,
                                    subCount = subCount,
                                    dubCount = dubCount
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSeasons API error: ${e.message}", e)
        }

        try {
            val targetName = (animeTitle ?: watchUrlOrSlug).trim()
            val cleanTarget = sanitizeSearchTitle(targetName)
            val baseFranchise = cleanTarget
                .substringBefore(":")
                .substringBefore("–")
                .substringBefore("-")
                .replace(Regex("""(?i)\b(season|part|cour|the movie|movie|final|ova|special|chapter|arc)\b.*"""), "")
                .trim()

            if (baseFranchise.length >= 3) {
                val franchiseResults = searchOrFilterAnime(keyword = baseFranchise)
                val baseWords = baseFranchise.lowercase().split(Regex("""\s+""")).filter { it.length >= 3 }
                for (item in franchiseResults) {
                    val itemTitleLower = item.title.lowercase()
                    val matchesFranchise = baseWords.isNotEmpty() && baseWords.all { itemTitleLower.contains(it) }
                    if (matchesFranchise && item.watchUrl.isNotBlank()) {
                        val normWatch = item.watchUrl.removeSuffix("/").lowercase()
                        val normTitle = item.title.lowercase().trim()
                        if (!seenWatchUrls.contains(normWatch) && !seenTitles.contains(normTitle)) {
                            seenWatchUrls.add(normWatch)
                            seenTitles.add(normTitle)
                            rawSeasonsList.add(
                                AnikotoSeason(
                                    number = rawSeasonsList.size + 1,
                                    title = item.title,
                                    watchUrl = item.watchUrl,
                                    posterUrl = item.posterUrl,
                                    subCount = item.subCount,
                                    dubCount = item.dubCount
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Franchise discovery error: ${e.message}")
        }

        if (rawSeasonsList.isEmpty()) {
            rawSeasonsList.add(AnikotoSeason(number = 1, title = animeTitle ?: "Season 1", watchUrl = watchUrlOrSlug))
        }

        val sortedSeasons = rawSeasonsList.sortedWith(
            compareBy<AnikotoSeason> { getSeasonSortRank(it.title).first }
                .thenBy { getSeasonSortRank(it.title).second }
                .thenBy { it.title }
        )

        val finalSeasons = sortedSeasons.mapIndexed { index, season ->
            season.copy(number = index + 1)
        }

        Log.d(TAG, "fetchSeasons resolved ${finalSeasons.size} parts/seasons for: ${animeTitle ?: watchUrlOrSlug}")
        finalSeasons
    }

    private fun getSeasonSortRank(title: String): Pair<Int, Int> {
        val lower = title.lowercase()
        val isSpecial = lower.contains("movie") || lower.contains("ova") || lower.contains("special") || lower.contains("the movie")
        
        val sRegex = Regex("""(?i)(?:season|s)\s*(\d+)""")
        val sMatch = sRegex.find(lower)
        var seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull()

        if (seasonNum == null) {
            seasonNum = when {
                lower.contains(" season 2") || lower.contains(" 2nd season") || lower.contains(" ii ") || lower.endsWith(" ii") || lower.contains(" ii:") -> 2
                lower.contains(" season 3") || lower.contains(" 3rd season") || lower.contains(" iii ") || lower.endsWith(" iii") || lower.contains(" iii:") -> 3
                lower.contains(" season 4") || lower.contains(" 4th season") || lower.contains(" iv ") || lower.endsWith(" iv") || lower.contains(" iv:") -> 4
                lower.contains(" season 5") || lower.contains(" 5th season") || lower.contains(" v ") || lower.endsWith(" v") || lower.contains(" v:") -> 5
                isSpecial -> 999
                else -> 1
            }
        }

        val pRegex = Regex("""(?i)(?:part|cour)\s*(\d+)""")
        val pMatch = pRegex.find(lower)
        var partNum = pMatch?.groupValues?.get(1)?.toIntOrNull()

        if (partNum == null) {
            partNum = when {
                lower.contains("part 2") || lower.contains("cour 2") || lower.contains("part ii") || lower.endsWith("part 2") -> 2
                lower.contains("part 3") || lower.contains("cour 3") || lower.contains("part iii") -> 3
                lower.contains("part 4") || lower.contains("cour 4") || lower.contains("part iv") -> 4
                else -> 1
            }
        }

        return Pair(if (isSpecial) 999 else seasonNum, partNum)
    }

    /**
     * 3. Fetch Anime Details from API
     */
    suspend fun fetchAnimeDetails(watchUrlOrSlug: String, episode: Int = 1): AnikotoDetails? = withContext(Dispatchers.IO) {
        try {
            val queryParams = mutableListOf<String>()
            if (watchUrlOrSlug.startsWith("http") || watchUrlOrSlug.contains("/watch/")) {
                queryParams.add("url=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            } else {
                queryParams.add("keyword=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            }
            queryParams.add("ep=$episode")

            val url = "$API_BASE_URL/api/stream/get?${queryParams.joinToString("&")}"
            val json = fetchJson(url) ?: return@withContext null

            val animeObj = json.optJSONObject("anime")
            val title = animeObj?.optString("title") ?: animeObj?.optString("baseTitle") ?: ""
            val poster = animeObj?.optString("poster") ?: ""
            val totalEpisodes = animeObj?.optInt("totalEpisodes", 0) ?: 0
            val seasonsArray = json.optJSONArray("seasons")
            val episodesArray = json.optJSONArray("allEpisodes")

            val epCount = if (totalEpisodes > 0) totalEpisodes else (episodesArray?.length() ?: 12)
            val seasonCount = if (seasonsArray != null && seasonsArray.length() > 0) seasonsArray.length() else 1

            var finalPoster = sanitizePosterUrl(poster)
            var finalDesc = ""
            var finalRating = "8.5"
            var finalYear = "2024"

            if (title.isNotBlank()) {
                val enhanced = AnimePosterEngine.getAnimePosterAndBanner(title)
                if (enhanced != null) {
                    if (finalPoster.isBlank() || enhanced.posterUrl.isNotBlank()) {
                        finalPoster = enhanced.posterUrl.ifBlank { finalPoster }
                    }
                    if (enhanced.description.isNotBlank()) finalDesc = enhanced.description
                    if (enhanced.rating.isNotBlank()) finalRating = enhanced.rating
                    if (enhanced.year.isNotBlank()) finalYear = enhanced.year
                }
            }

            return@withContext AnikotoDetails(
                title = title,
                posterUrl = finalPoster,
                description = finalDesc,
                rating = finalRating,
                releaseYear = finalYear,
                totalSeasons = seasonCount,
                totalEpisodes = epCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchAnimeDetails error: ${e.message}", e)
            null
        }
    }

    /**
     * 4. Fetch Episode List
     */
    suspend fun fetchEpisodes(watchUrlOrSlug: String, episode: Int = 1): List<AnikotoEpisode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<AnikotoEpisode>()
        try {
            val cleanSlug = watchUrlOrSlug.substringAfterLast("/watch/").substringBefore("?").trimEnd('/')
            if (cleanSlug.isNotBlank() && !cleanSlug.startsWith("http")) {
                val epUrl = "$API_BASE_URL/api/anikoto/episodes/${URLEncoder.encode(cleanSlug, "UTF-8")}"
                val epJson = fetchJson(epUrl)
                val epList = epJson?.optJSONArray("episodes") ?: epJson?.optJSONArray("allEpisodes")
                if (epList != null && epList.length() > 0) {
                    for (i in 0 until epList.length()) {
                        val epObj = epList.optJSONObject(i) ?: continue
                        val epNum = epObj.optString("episode", "${i + 1}").toIntOrNull() ?: (i + 1)
                        val epTitle = epObj.optString("title", "Episode $epNum")
                        val epId = epObj.optString("id", "$epNum")
                        episodes.add(
                            AnikotoEpisode(
                                id = epId,
                                number = epNum,
                                title = if (epTitle.isBlank()) "Episode $epNum" else epTitle,
                                dataIdsToken = epId
                            )
                        )
                    }
                }
            }

            if (episodes.isEmpty()) {
                val queryParams = mutableListOf<String>()
                if (watchUrlOrSlug.startsWith("http") || watchUrlOrSlug.contains("/watch/")) {
                    queryParams.add("url=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
                } else {
                    queryParams.add("keyword=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
                }
                queryParams.add("ep=$episode")

                val url = "$API_BASE_URL/api/stream/get?${queryParams.joinToString("&")}"
                Log.d(TAG, "Fetching Episodes from API: $url")
                val json = fetchJson(url)

                if (json != null) {
                    val allEpisodesArray = json.optJSONArray("allEpisodes")
                    if (allEpisodesArray != null && allEpisodesArray.length() > 0) {
                        for (i in 0 until allEpisodesArray.length()) {
                            val epObj = allEpisodesArray.optJSONObject(i) ?: continue
                            val epNum = epObj.optString("episode", "${i + 1}").toIntOrNull() ?: (i + 1)
                            val epTitle = epObj.optString("title", "Episode $epNum")
                            val epId = epObj.optString("id", "$epNum")
                            episodes.add(
                                AnikotoEpisode(
                                    id = epId,
                                    number = epNum,
                                    title = if (epTitle.isBlank()) "Episode $epNum" else epTitle,
                                    dataIdsToken = epId
                                )
                            )
                        }
                    } else {
                        val animeObj = json.optJSONObject("anime")
                        val totalEp = animeObj?.optInt("totalEpisodes", 12) ?: 12
                        for (i in 1..totalEp) {
                            episodes.add(AnikotoEpisode(id = "$i", number = i, title = "Episode $i", dataIdsToken = "$i"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchEpisodes error: ${e.message}", e)
        }

        if (episodes.isEmpty()) {
            val directEps = scrapeAnikotoEpisodesDirect(watchUrlOrSlug)
            if (directEps.isNotEmpty()) {
                episodes.addAll(directEps)
            }
        }

        if (episodes.isEmpty()) {
            for (i in 1..12) {
                episodes.add(AnikotoEpisode(id = "$i", number = i, title = "Episode $i", dataIdsToken = "$i"))
            }
        }

        episodes.distinctBy { it.number }.sortedBy { it.number }
    }

    /**
     * 5. REVERSE-ENGINEERED MEGAPLAY KEY EXCHANGE & TRUSTWATCH HANDSHAKE
     * Executes encrypted stream handshake against https://megaplay.buzz/stream/trustWatch
     */
    suspend fun performMegaplayHandshake(
        sourceId: String,
        episodeId: String = ""
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initiating Megaplay trustWatch handshake for sourceId=$sourceId (ep=$episodeId)")
            val trustWatchUrl = "https://megaplay.buzz/stream/trustWatch"
            val headers = mapOf(
                "User-Agent" to DEFAULT_UA,
                "Referer" to "https://anikoto.cz/",
                "Origin" to "https://megaplay.buzz",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            )

            val params = mutableMapOf(
                "id" to sourceId,
                "ep" to episodeId.ifBlank { "1" }
            )

            val json = postJson(trustWatchUrl, params, headers)
            if (json != null) {
                var streamUrl = ""
                if (json.has("data")) {
                    val rawEnc = json.optString("data", "")
                    val decrypted = UniversalAnimeDownloadScraper.decryptMegaplayEnc(rawEnc)
                    if (!decrypted.isNullOrBlank()) {
                        streamUrl = decrypted
                    }
                }
                if (streamUrl.isBlank() && json.has("file")) {
                    streamUrl = json.optString("file", "")
                }
                if (streamUrl.isBlank() && json.has("sources")) {
                    val srcArr = json.optJSONArray("sources")
                    if (srcArr != null && srcArr.length() > 0) {
                        streamUrl = srcArr.getJSONObject(0).optString("file", "")
                    }
                }

                if (streamUrl.isNotBlank()) {
                    val directSubtitles = buildFullSubtitleTracks(sourceId)
                    val tracksFromResp = parseSubtitles(json.optJSONArray("tracks"))
                    val finalTracks = if (tracksFromResp.isNotEmpty()) tracksFromResp else directSubtitles

                    Log.d(TAG, "Megaplay trustWatch handshake successful: $streamUrl with ${finalTracks.size} subtitles")
                    return@withContext ScrapedStreamResult(
                        streamUrl = streamUrl,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to "https://anikoto.cz/",
                            "Origin" to "https://megaplay.buzz"
                        ),
                        referer = "https://anikoto.cz/",
                        subtitles = finalTracks
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "performMegaplayHandshake error: ${e.message}")
        }
        null
    }

    /**
     * 6. REVERSE-ENGINEERED NEKOSTREAM & MAPPER SERVER RESOLVER
     * Maps stream keys to upstream proxies: Vidstream-2, Vidstream 1 BETA, HD-1, HD-2, Kiwi Sub
     */
    suspend fun resolveNekostreamMapping(episodeId: String): List<AnikotoServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<AnikotoServer>()
        if (episodeId.isBlank()) return@withContext servers

        try {
            val mapperUrl = "https://mapper.nekostream.site/ep/ep/$episodeId"
            val headers = mapOf(
                "User-Agent" to DEFAULT_UA,
                "Referer" to "https://anikoto.cz/",
                "Origin" to "https://anikoto.cz"
            )
            val json = fetchJson(mapperUrl, headers)
            if (json != null) {
                val srvObj = json.optJSONObject("servers") ?: json
                val keys = srvObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val sData = srvObj.optJSONObject(k)
                    val sUrl = sData?.optString("url") ?: srvObj.optString(k, "")
                    val sType = if (k.contains("dub", ignoreCase = true)) "dub" else "sub"
                    val displayName = when {
                        k.contains("vidstream-2", ignoreCase = true) -> "Vidstream-2"
                        k.contains("vidstream", ignoreCase = true) -> "Vidstream 1 BETA"
                        k.contains("hd-2", ignoreCase = true) -> "HD-2"
                        k.contains("hd-1", ignoreCase = true) || k.contains("hd1", ignoreCase = true) -> "HD-1"
                        k.contains("kiwi", ignoreCase = true) -> "Kiwi Sub"
                        else -> k
                    }

                    if (sUrl.isNotBlank()) {
                        servers.add(
                            AnikotoServer(
                                id = displayName,
                                linkId = sUrl,
                                name = displayName,
                                type = sType,
                                streamUrl = if (sUrl.contains(".m3u8") || sUrl.contains("/stream/")) sUrl else "",
                                rawUrl = sUrl,
                                referer = "https://anikoto.cz/",
                                tracks = buildFullSubtitleTracks(episodeId)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveNekostreamMapping error for ep $episodeId: ${e.message}")
        }
        servers
    }

    /**
     * 7. Fetch available SUB & DUB streaming servers in parallel
     */
    suspend fun fetchAvailableServers(
        title: String,
        season: Int = 1,
        episode: Int = 1
    ): AnikotoServerGroup = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = sanitizeSearchTitle(title)
            val rawClean = title.removePrefix("anikoto_").removePrefix("movie_").removePrefix("series_").trim()
            val effectiveEp = if (episode <= 0) 1 else episode
            Log.d(TAG, "Fetching available SUB / DUB servers for: $cleanTitle (raw: $rawClean, Ep $effectiveEp)")

            val subServers = mutableListOf<AnikotoServer>()
            val dubServers = mutableListOf<AnikotoServer>()
            var resolvedWatchUrl = ""

            // 1. Resolve exact Anikoto watch URL or slug
            val directOrResolvedUrl = resolveAnikotoWatchUrl(rawClean) ?: resolveAnikotoWatchUrl(cleanTitle)
            if (!directOrResolvedUrl.isNullOrBlank()) {
                resolvedWatchUrl = directOrResolvedUrl
            }

            // Check Nekostream mapping if episode ID is extractable
            val epNumToken = "$effectiveEp"
            val nekostreamServers = resolveNekostreamMapping(epNumToken)
            for (nsSrv in nekostreamServers) {
                if (nsSrv.type == "dub") dubServers.add(nsSrv) else subServers.add(nsSrv)
            }

            val queriesToTry = mutableListOf<String>()
            if (!directOrResolvedUrl.isNullOrBlank()) {
                queriesToTry.add(directOrResolvedUrl)
            }
            if (rawClean.startsWith("http://") || rawClean.startsWith("https://") || rawClean.contains("/watch/")) {
                if (!queriesToTry.contains(rawClean)) queriesToTry.add(rawClean)
            } else if (isAnikotoSlug(rawClean)) {
                val slugWatch = "https://anikoto.cz/watch/$rawClean/ep-$effectiveEp"
                if (!queriesToTry.contains(slugWatch)) queriesToTry.add(slugWatch)
                if (!queriesToTry.contains(rawClean)) queriesToTry.add(rawClean)
            }
            if (!queriesToTry.contains(cleanTitle)) queriesToTry.add(cleanTitle)

            val withoutArticle = cleanTitle.replace(Regex("^(The|A|An)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (withoutArticle.isNotBlank() && !queriesToTry.contains(withoutArticle)) {
                queriesToTry.add(withoutArticle)
            }

            if (season > 1) {
                queriesToTry.add("$cleanTitle Season $season")
                if (withoutArticle.isNotBlank()) queriesToTry.add("$withoutArticle Season $season")
            }

            val baseFranchise = cleanTitle.substringBefore(":").substringBefore("-").trim()
            if (baseFranchise.isNotBlank() && !queriesToTry.contains(baseFranchise)) {
                if (season > 1) queriesToTry.add("$baseFranchise Season $season")
                queriesToTry.add(baseFranchise)
            }

            // Execute parallel fetch for candidate queries across SUB & DUB
            for (queryCandidate in queriesToTry) {
                if (subServers.isNotEmpty() && dubServers.isNotEmpty()) break

                val subDeferred = async {
                    val subUrl = buildStreamGetUrl(title = queryCandidate, episode = effectiveEp, type = "sub")
                    fetchJson(subUrl)
                }
                val dubDeferred = async {
                    val dubUrl = buildStreamGetUrl(title = queryCandidate, episode = effectiveEp, type = "dub")
                    fetchJson(dubUrl)
                }

                val subJson = subDeferred.await()
                val dubJson = dubDeferred.await()

                if (subJson != null && subJson.optBoolean("success") == true) {
                    if (resolvedWatchUrl.isBlank()) {
                        val animeWatch = subJson.optJSONObject("anime")?.optString("watchUrl", "") ?: ""
                        if (animeWatch.isNotBlank()) resolvedWatchUrl = animeWatch
                    }

                    val selectedStreamObj = subJson.optJSONObject("selectedStream")
                    if (selectedStreamObj != null) {
                        val sUrl = makeAbsoluteUrl(selectedStreamObj.optString("streamUrl", ""))
                        if (sUrl.isNotBlank()) {
                            val referer = selectedStreamObj.optString("referer", "https://anikoto.cz/")
                            val srvId = selectedStreamObj.optString("server", "Vidstream-2")
                            val srvAudio = selectedStreamObj.optString("audioType", "SUB").uppercase()
                            val tracks = parseSubtitles(selectedStreamObj.optJSONArray("tracks")).ifEmpty {
                                buildFullSubtitleTracks(srvId)
                            }
                            val prioServer = AnikotoServer(
                                id = srvId,
                                linkId = sUrl,
                                name = srvId,
                                type = srvAudio.lowercase(),
                                streamUrl = sUrl,
                                rawUrl = sUrl,
                                referer = referer,
                                tracks = tracks
                            )
                            if (srvAudio == "DUB") {
                                if (dubServers.none { it.id == prioServer.id }) dubServers.add(0, prioServer)
                            } else {
                                if (subServers.none { it.id == prioServer.id }) subServers.add(0, prioServer)
                            }
                        }
                    }

                    val availableServers = subJson.optJSONArray("availableServers")
                    if (availableServers != null && availableServers.length() > 0) {
                        for (i in 0 until availableServers.length()) {
                            val srvObj = availableServers.optJSONObject(i) ?: continue
                            val srvName = srvObj.optString("name", "Server ${i + 1}")
                            val srvId = srvObj.optString("server", "HD-${i + 1}")
                            val audioType = srvObj.optString("audioType", "SUB").uppercase()
                            val isPlayableDirectly = srvObj.optBoolean("isPlayableDirectly", true)
                            val streamUrl = makeAbsoluteUrl(srvObj.optString("streamUrl", ""))
                            val rawUrl = srvObj.optString("rawUrl", "")
                            val referer = srvObj.optString("referer", "https://anikoto.cz/")
                            val tracks = parseSubtitles(srvObj.optJSONArray("tracks")).ifEmpty {
                                buildFullSubtitleTracks(srvId)
                            }

                            val isDownloadMirror = !isPlayableDirectly || streamUrl.contains("pahe") || streamUrl.contains("nekostream")
                            val displayName = when {
                                srvName.contains("Vidstream-2", ignoreCase = true) || srvId.contains("Vidstream-2", ignoreCase = true) -> "Vidstream-2"
                                srvName.contains("Vidstream", ignoreCase = true) || srvId.contains("Vidstream", ignoreCase = true) -> "Vidstream 1 BETA"
                                srvName.contains("HD-1", ignoreCase = true) || srvId.contains("HD-1", ignoreCase = true) -> "HD-1"
                                srvName.contains("HD-2", ignoreCase = true) || srvId.contains("HD-2", ignoreCase = true) -> "HD-2"
                                srvName.contains("Kiwi", ignoreCase = true) || srvId.contains("Kiwi", ignoreCase = true) -> "Kiwi Sub"
                                isDownloadMirror && (srvId.contains("p") || srvId.contains("1080") || srvId.contains("720")) -> "$srvId (Mirror)"
                                srvName.isNotBlank() && !srvName.startsWith("DEMON KING") -> srvName
                                else -> "Vidstream-${i + 1}"
                            }

                            val parsedServer = AnikotoServer(
                                id = srvId,
                                linkId = streamUrl.ifBlank { rawUrl },
                                name = displayName,
                                type = audioType.lowercase(),
                                streamUrl = streamUrl,
                                rawUrl = rawUrl,
                                referer = referer,
                                tracks = tracks
                            )

                            if (audioType == "DUB") {
                                if (dubServers.none { it.id == parsedServer.id && it.streamUrl == parsedServer.streamUrl }) {
                                    if (isDownloadMirror) dubServers.add(parsedServer) else dubServers.add(0, parsedServer)
                                }
                            } else {
                                if (subServers.none { it.id == parsedServer.id && it.streamUrl == parsedServer.streamUrl }) {
                                    if (isDownloadMirror) subServers.add(parsedServer) else subServers.add(0, parsedServer)
                                }
                            }
                        }
                    }
                }

                if (dubJson != null && dubJson.optBoolean("success") == true) {
                    val dubStreamObj = dubJson.optJSONObject("selectedStream")
                    if (dubStreamObj != null) {
                        val sUrl = makeAbsoluteUrl(dubStreamObj.optString("streamUrl", ""))
                        if (sUrl.isNotBlank()) {
                            val referer = dubStreamObj.optString("referer", "https://anikoto.cz/")
                            val srvId = dubStreamObj.optString("server", "Vidstream-2")
                            val prioServer = AnikotoServer(
                                id = srvId,
                                linkId = sUrl,
                                name = srvId,
                                type = "dub",
                                streamUrl = sUrl,
                                rawUrl = sUrl,
                                referer = referer,
                                tracks = emptyList()
                            )
                            if (dubServers.none { it.id == prioServer.id }) {
                                dubServers.add(0, prioServer)
                            }
                        }
                    }

                    val availableServers = dubJson.optJSONArray("availableServers")
                    if (availableServers != null && availableServers.length() > 0) {
                        for (i in 0 until availableServers.length()) {
                            val srvObj = availableServers.optJSONObject(i) ?: continue
                            val srvId = srvObj.optString("server", "HD-${i + 1}")
                            val srvName = srvObj.optString("name", "Server ${i + 1}")
                            val audioType = "dub"
                            val streamUrl = makeAbsoluteUrl(srvObj.optString("streamUrl", ""))
                            val rawUrl = srvObj.optString("rawUrl", "")
                            val referer = srvObj.optString("referer", "https://anikoto.cz/")
                            val tracks = parseSubtitles(srvObj.optJSONArray("tracks"))

                            val displayName = when {
                                srvName.contains("Vidstream-2", ignoreCase = true) || srvId.contains("Vidstream-2", ignoreCase = true) -> "Vidstream-2"
                                srvName.contains("Vidstream", ignoreCase = true) || srvId.contains("Vidstream", ignoreCase = true) -> "Vidstream 1 BETA"
                                srvName.contains("HD-1", ignoreCase = true) || srvId.contains("HD-1", ignoreCase = true) -> "HD-1"
                                srvName.contains("HD-2", ignoreCase = true) || srvId.contains("HD-2", ignoreCase = true) -> "HD-2"
                                srvName.isNotBlank() && !srvName.startsWith("DEMON KING") -> srvName
                                else -> "Vidstream-${i + 1}"
                            }

                            val parsedServer = AnikotoServer(
                                id = srvId,
                                linkId = streamUrl.ifBlank { rawUrl },
                                name = displayName,
                                type = "dub",
                                streamUrl = streamUrl,
                                rawUrl = rawUrl,
                                referer = referer,
                                tracks = tracks
                            )
                            if (dubServers.none { it.id == parsedServer.id && it.streamUrl == parsedServer.streamUrl }) {
                                dubServers.add(parsedServer)
                            }
                        }
                    }
                }
            }

            // Ensure standard multi-server defaults exist
            if (subServers.isEmpty()) {
                subServers.add(
                    AnikotoServer(
                        id = "Vidstream-2",
                        linkId = "Vidstream-2",
                        name = "Vidstream-2 (SUB)",
                        type = "sub",
                        streamUrl = "",
                        rawUrl = "",
                        referer = "https://anikoto.cz/",
                        tracks = buildFullSubtitleTracks(title)
                    )
                )
                subServers.add(
                    AnikotoServer(
                        id = "Vidstream 1 BETA",
                        linkId = "Vidstream 1 BETA",
                        name = "Vidstream 1 BETA (SUB)",
                        type = "sub",
                        streamUrl = "",
                        rawUrl = "",
                        referer = "https://anikoto.cz/",
                        tracks = buildFullSubtitleTracks(title)
                    )
                )
                subServers.add(
                    AnikotoServer(
                        id = "HD-1",
                        linkId = "HD-1",
                        name = "HD-1 (SUB)",
                        type = "sub",
                        streamUrl = "",
                        rawUrl = "",
                        referer = "https://anikoto.cz/",
                        tracks = buildFullSubtitleTracks(title)
                    )
                )
            }

            if (dubServers.isEmpty()) {
                dubServers.add(
                    AnikotoServer(
                        id = "Vidstream-2-DUB",
                        linkId = "Vidstream-2-DUB",
                        name = "Vidstream-2 (DUB)",
                        type = "dub",
                        streamUrl = "",
                        rawUrl = "",
                        referer = "https://anikoto.cz/",
                        tracks = emptyList()
                    )
                )
                dubServers.add(
                    AnikotoServer(
                        id = "Vidstream 1 BETA-DUB",
                        linkId = "Vidstream 1 BETA-DUB",
                        name = "Vidstream 1 BETA (DUB)",
                        type = "dub",
                        streamUrl = "",
                        rawUrl = "",
                        referer = "https://anikoto.cz/",
                        tracks = emptyList()
                    )
                )
            }

            val cleanSub = subServers.distinctBy { it.id.ifBlank { it.streamUrl } }
            val cleanDub = dubServers.distinctBy { it.id.ifBlank { it.streamUrl } }
            val finalWatchUrl = resolvedWatchUrl.ifBlank { directOrResolvedUrl ?: title }

            Log.d(TAG, "Resolved ${cleanSub.size} SUB servers and ${cleanDub.size} DUB servers for $cleanTitle (watchUrl: $finalWatchUrl)")
            AnikotoServerGroup(
                subServers = cleanSub,
                dubServers = cleanDub,
                watchUrl = finalWatchUrl,
                episodeNum = episode
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchAvailableServers error: ${e.message}", e)
            val fallbackSub = listOf(
                AnikotoServer(id = "Vidstream-2", linkId = "Vidstream-2", name = "Vidstream-2", type = "sub", referer = "https://anikoto.cz/"),
                AnikotoServer(id = "HD-1", linkId = "HD-1", name = "HD-1", type = "sub", referer = "https://anikoto.cz/")
            )
            AnikotoServerGroup(fallbackSub, emptyList(), title, episode)
        }
    }

    /**
     * 8. Extract Direct Playable Stream from a specific selected server
     */
    suspend fun extractStreamFromServer(
        server: AnikotoServer,
        watchUrl: String = "",
        episode: Int = 1,
        season: Int = 1
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val isDub = server.type.lowercase() == "dub"
            val targetType = if (isDub) "dub" else "sub"
            val effectiveEp = if (episode <= 0) 1 else episode
            val cleanKw = watchUrl.removePrefix("anikoto_").trim()

            // 1. Check direct playable streamUrl
            if (server.streamUrl.isNotBlank() && (server.streamUrl.contains("/api/stream/") || server.streamUrl.contains("/api/proxy") || server.streamUrl.contains(".m3u8") || server.streamUrl.contains("master.m3u8"))) {
                val finalUrl = makeAbsoluteUrl(server.streamUrl)
                val referer = if (server.referer.isNotBlank()) server.referer else "https://anikoto.cz/"
                Log.d(TAG, "extractStreamFromServer using direct server streamUrl: $finalUrl")
                return@withContext ScrapedStreamResult(
                    streamUrl = finalUrl,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to referer,
                        "Origin" to "https://anikoto.cz"
                    ),
                    referer = referer,
                    subtitles = server.tracks
                )
            }

            // 2. If linkId is an AJAX server ID (numeric), query Anikoto sources endpoint directly
            val numericId = Regex("""^\d+$""").find(server.linkId.trim())?.value
            if (!numericId.isNullOrBlank()) {
                val sourcesUrls = listOf(
                    "https://anikoto.cz/ajax/v2/episode/sources?id=$numericId",
                    "https://anikoto.cz/ajax/episode/sources?id=$numericId"
                )
                for (srcUrl in sourcesUrls) {
                    try {
                        val srcJson = fetchJson(
                            url = srcUrl,
                            customHeaders = mapOf(
                                "User-Agent" to DEFAULT_UA,
                                "Referer" to "https://anikoto.cz/",
                                "X-Requested-With" to "XMLHttpRequest"
                            )
                        )
                        val iframeLink = srcJson?.optString("link", "") ?: ""
                        if (iframeLink.isNotBlank()) {
                            Log.d(TAG, "Resolving iframe embed from Anikoto sources: $iframeLink")
                            val resolved = UniversalAnimeDownloadScraper.resolveDirectMediaStream(
                                embedUrl = iframeLink,
                                fallbackReferer = "https://anikoto.cz/"
                            )
                            if (resolved.directUrl.isNotBlank()) {
                                return@withContext ScrapedStreamResult(
                                    streamUrl = resolved.directUrl,
                                    headers = mapOf(
                                        "User-Agent" to DEFAULT_UA,
                                        "Referer" to resolved.referer,
                                        "Origin" to "https://anikoto.cz"
                                    ),
                                    referer = resolved.referer,
                                    subtitles = resolved.tracks.map {
                                        SubtitleTrack(url = it.url, lang = it.lang, label = it.label, default = it.default)
                                    }
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Direct sources resolution error: ${e.message}")
                    }
                }
            }

            // 3. Megaplay trustWatch handshake
            val sourceIdMatch = Regex("""\d+""").find(server.linkId.ifBlank { server.rawUrl })?.value
                ?: Regex("""\d+""").find(cleanKw)?.value
            if (!sourceIdMatch.isNullOrBlank()) {
                val megaplayRes = performMegaplayHandshake(sourceIdMatch, "$effectiveEp")
                if (megaplayRes != null && megaplayRes.streamUrl.isNotBlank()) {
                    return@withContext megaplayRes
                }
            }

            // 4. Query the stream API with server ID and specific requested episode
            val cleanServerId = server.id.replace(Regex("""(?i)[-_ ]*(?:dub|sub)"""), "").trim()
            val queryUrl = buildStreamGetUrl(
                title = if (cleanKw.isNotBlank()) cleanKw else cleanServerId,
                episode = effectiveEp,
                type = targetType,
                server = if (cleanServerId.contains("p") || cleanServerId.contains("Mirror")) null else cleanServerId
            )
            Log.d(TAG, "extractStreamFromServer querying: $queryUrl for Ep $effectiveEp")
            val json = fetchJson(queryUrl)
            val selected = json?.optJSONObject("selectedStream")
            if (selected != null) {
                val sUrl = makeAbsoluteUrl(selected.optString("streamUrl", ""))
                if (sUrl.isNotBlank()) {
                    val referer = selected.optString("referer", "https://anikoto.cz/")
                    val subtitles = parseSubtitles(selected.optJSONArray("tracks")).ifEmpty { server.tracks }
                    return@withContext ScrapedStreamResult(
                        streamUrl = sUrl,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to referer,
                            "Origin" to "https://anikoto.cz"
                        ),
                        referer = referer,
                        subtitles = if (subtitles.isNotEmpty()) subtitles else server.tracks
                    )
                }
            }

            // Fallback 5: Direct embed resolution
            val embedCandidate = server.linkId.ifBlank { server.rawUrl }
            if (embedCandidate.isNotBlank() && embedCandidate.startsWith("http")) {
                val nativeResolved = UniversalAnimeDownloadScraper.resolveDirectMediaStream(
                    embedUrl = embedCandidate,
                    fallbackReferer = if (server.referer.isNotBlank()) server.referer else "https://anikoto.cz/"
                )
                if (nativeResolved.directUrl.isNotBlank()) {
                    return@withContext ScrapedStreamResult(
                        streamUrl = nativeResolved.directUrl,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to nativeResolved.referer,
                            "Origin" to "https://anikoto.cz"
                        ),
                        referer = nativeResolved.referer,
                        subtitles = nativeResolved.tracks.map {
                            SubtitleTrack(
                                url = it.url,
                                lang = it.lang,
                                label = it.label,
                                default = it.default
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractStreamFromServer error: ${e.message}", e)
        }
        null
    }

    /**
     * 9. Primary Endpoint: Get Instant Stream M3U8 & Subtitles from API
     */
    suspend fun getStreamByTitle(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        preferDub: Boolean = false,
        requestedServerKey: String? = null
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val effectiveEp = if (episode <= 0) 1 else episode
            val audioType = if (preferDub) "dub" else "sub"
            val cleanTitle = sanitizeSearchTitle(title)
            val rawClean = title.removePrefix("anikoto_").removePrefix("movie_").removePrefix("series_").trim()
            Log.d(TAG, "High-Power Anime API stream fetch for: $cleanTitle (S$season Ep $effectiveEp, audio: $audioType, server: $requestedServerKey)")

            val directOrResolvedUrl = resolveAnikotoWatchUrl(rawClean) ?: resolveAnikotoWatchUrl(cleanTitle)
            val queriesToTry = mutableListOf<String>()
            if (!directOrResolvedUrl.isNullOrBlank()) {
                queriesToTry.add(directOrResolvedUrl)
            }
            if (rawClean.startsWith("http://") || rawClean.startsWith("https://") || rawClean.contains("/watch/")) {
                if (!queriesToTry.contains(rawClean)) queriesToTry.add(rawClean)
            } else if (isAnikotoSlug(rawClean)) {
                val slugWatch = "https://anikoto.cz/watch/$rawClean/ep-$effectiveEp"
                if (!queriesToTry.contains(slugWatch)) queriesToTry.add(slugWatch)
                if (!queriesToTry.contains(rawClean)) queriesToTry.add(rawClean)
            }
            if (!queriesToTry.contains(cleanTitle)) queriesToTry.add(cleanTitle)

            val withoutArticle = cleanTitle.replace(Regex("^(The|A|An)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (withoutArticle.isNotBlank() && !queriesToTry.contains(withoutArticle)) {
                queriesToTry.add(withoutArticle)
            }

            val cleanServer = requestedServerKey?.replace(Regex("""(?i)[-_ ]*(?:dub|sub)"""), "")?.trim()
            var json: JSONObject? = null

            for (candidate in queriesToTry) {
                val apiUrl = buildStreamGetUrl(
                    title = candidate,
                    episode = effectiveEp,
                    type = audioType,
                    server = cleanServer
                )
                val res = fetchJson(apiUrl)
                if (res != null && res.optBoolean("success") == true) {
                    val stream = res.optJSONObject("selectedStream")
                    val servers = res.optJSONArray("availableServers")
                    if (stream != null || (servers != null && servers.length() > 0)) {
                        json = res
                        break
                    }
                }
            }

            if (json != null && json.optBoolean("success") == true) {
                val selectedStream = json.optJSONObject("selectedStream")
                if (selectedStream != null) {
                    val rawStreamUrl = selectedStream.optString("streamUrl", "")
                    if (rawStreamUrl.isNotBlank()) {
                        val finalStreamUrl = makeAbsoluteUrl(rawStreamUrl)
                        val referer = selectedStream.optString("referer", "$API_BASE_URL/")
                        val subtitles = parseSubtitles(selectedStream.optJSONArray("tracks")).ifEmpty {
                            buildFullSubtitleTracks(title)
                        }

                        Log.d(TAG, "Anime Stream API resolved: $finalStreamUrl with ${subtitles.size} subtitles")
                        return@withContext ScrapedStreamResult(
                            streamUrl = finalStreamUrl,
                            headers = mapOf(
                                "User-Agent" to DEFAULT_UA,
                                "Referer" to referer,
                                "Origin" to "https://anikoto.cz"
                            ),
                            referer = referer,
                            subtitles = subtitles
                        )
                    }
                }

                val availableServers = json.optJSONArray("availableServers")
                if (availableServers != null && availableServers.length() > 0) {
                    for (i in 0 until availableServers.length()) {
                        val sObj = availableServers.optJSONObject(i) ?: continue
                        val rawStream = sObj.optString("streamUrl", "").ifBlank { sObj.optString("rawUrl", "") }
                        if (rawStream.isNotBlank()) {
                            val resolvedUrl = makeAbsoluteUrl(rawStream)
                            val referer = sObj.optString("referer", "$API_BASE_URL/")
                            Log.d(TAG, "Anime Stream resolved via availableServers array: $resolvedUrl")
                            return@withContext ScrapedStreamResult(
                                streamUrl = resolvedUrl,
                                headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to referer, "Origin" to "https://anikoto.cz"),
                                referer = referer,
                                subtitles = buildFullSubtitleTracks(title)
                            )
                        }
                    }
                }
            }

            // Fallback: extract via fetchAvailableServers (strictly matching audioType)
            try {
                val serverGroup = fetchAvailableServers(title = cleanTitle, season = season, episode = effectiveEp)
                val targetServers = if (preferDub) serverGroup.dubServers else serverGroup.subServers
                val candidateServers = if (!requestedServerKey.isNullOrBlank()) {
                    val normReq = cleanServer?.lowercase() ?: ""
                    targetServers.filter { it.name.lowercase().contains(normReq) || it.id.lowercase().contains(normReq) }
                        .ifEmpty { targetServers }
                } else {
                    targetServers
                }

                for (srv in candidateServers) {
                    val serverStream = extractStreamFromServer(
                        server = srv,
                        watchUrl = directOrResolvedUrl ?: cleanTitle,
                        episode = effectiveEp
                    )
                    if (serverStream != null && serverStream.streamUrl.isNotBlank()) {
                        return@withContext ScrapedStreamResult(
                            streamUrl = serverStream.streamUrl,
                            headers = serverStream.headers,
                            referer = serverStream.referer,
                            subtitles = serverStream.subtitles
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Available servers extraction error: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getStreamByTitle error: ${e.message}", e)
        }
        null
    }

    /**
     * Resolves the exact Anikoto watchUrl by querying search with aliases & calculating title similarity
     */
    suspend fun resolveAnikotoWatchUrl(titleOrSlug: String): String? = withContext(Dispatchers.IO) {
        val raw = titleOrSlug.trim()
            .removePrefix("anikoto_")
            .removePrefix("movie_")
            .removePrefix("series_")
            .trim()

        if (raw.isBlank()) return@withContext null

        if (raw.startsWith("http://") || raw.startsWith("https://") || raw.contains("/watch/")) {
            val path = raw.substringAfter("/watch/").substringBefore("?").substringBefore("/")
            if (!path.all { it.isDigit() } && path.isNotBlank()) {
                return@withContext raw
            }
        }

        if (isAnikotoSlug(raw)) {
            return@withContext "https://anikoto.cz/watch/$raw"
        }

        val cleanTitle = sanitizeSearchTitle(raw)
        val withoutArticles = cleanTitle
            .replace(Regex("^(The|A|An)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

        val candidateQueries = mutableListOf<String>()
        if (cleanTitle.isNotBlank()) candidateQueries.add(cleanTitle)
        if (withoutArticles.isNotBlank() && !candidateQueries.contains(withoutArticles)) candidateQueries.add(withoutArticles)

        try {
            val metadata = AnimePosterEngine.getAnimePosterAndBanner(cleanTitle)
            if (metadata != null) {
                if (metadata.titleEnglish.isNotBlank() && !candidateQueries.contains(metadata.titleEnglish)) {
                    candidateQueries.add(metadata.titleEnglish)
                }
                if (metadata.titleRomaji.isNotBlank()) {
                    val romajiClean = sanitizeSearchTitle(metadata.titleRomaji)
                    if (!candidateQueries.contains(romajiClean)) candidateQueries.add(romajiClean)
                    val romajiShort = romajiClean.substringBefore(":").substringBefore("-").trim()
                    if (romajiShort.isNotBlank() && !candidateQueries.contains(romajiShort)) {
                        candidateQueries.add(romajiShort)
                    }
                }
            }
        } catch (_: Exception) {}

        val words = withoutArticles.split(Regex("\\s+")).filter { it.length >= 4 && !it.equals("season", true) }
        for (w in words) {
            if (!candidateQueries.contains(w)) candidateQueries.add(w)
        }

        for (query in candidateQueries) {
            val searchResults = searchOrFilterAnime(keyword = query)
            if (searchResults.isEmpty()) continue

            val bestMatch = searchResults.maxByOrNull { item ->
                scoreTitleMatch(target = cleanTitle, candidate = item.title, withoutArticles = withoutArticles)
            }

            if (bestMatch != null && bestMatch.watchUrl.isNotBlank()) {
                val score = scoreTitleMatch(target = cleanTitle, candidate = bestMatch.title, withoutArticles = withoutArticles)
                if (score > 30) {
                    Log.d(TAG, "Resolved watchUrl for '$raw' -> '${bestMatch.title}' (${bestMatch.watchUrl}) with score $score")
                    return@withContext bestMatch.watchUrl
                }
            }
        }

        null
    }

    private fun scoreTitleMatch(target: String, candidate: String, withoutArticles: String): Int {
        val t = target.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
        val c = candidate.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
        val wa = withoutArticles.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()

        if (t == c || wa == c) return 100
        if (c.contains(wa) || wa.contains(c)) return 90
        if (c.contains(t) || t.contains(c)) return 85

        val targetWords = wa.split(Regex("\\s+")).filter { it.length > 2 }
        val candidateWords = c.split(Regex("\\s+")).filter { it.length > 2 }
        if (targetWords.isNotEmpty() && candidateWords.isNotEmpty()) {
            val matchedWords = targetWords.count { tw -> candidateWords.any { cw -> cw == tw || (cw.length >= 4 && (cw.contains(tw) || tw.contains(cw))) } }
            val ratio = (matchedWords * 100) / targetWords.size
            if (ratio >= 50) return 50 + ratio / 2
        }

        return 0
    }

    private fun isAnikotoSlug(text: String): Boolean {
        val clean = text.trim().lowercase()
        if (clean.contains(" ") || clean.contains(":") || clean.contains("?") || clean.contains("/") || clean.contains("http")) return false
        return Regex("""^[a-z0-9]+(?:-[a-z0-9]+)+$""").matches(clean)
    }

    private fun buildStreamGetUrl(title: String, episode: Int, type: String, server: String? = null): String {
        val clean = title
            .removePrefix("anikoto_")
            .removePrefix("movie_")
            .removePrefix("series_")
            .trim()
        val epParam = if (episode <= 0) 1 else episode
        val queryParams = mutableListOf<String>()

        if (clean.startsWith("http://") || clean.startsWith("https://") || clean.contains("/watch/")) {
            val base = if (clean.contains("?")) clean.substringBefore("?") else clean
            val adjustedUrl = if (base.contains(Regex("""/ep-\d+"""))) {
                base.replace(Regex("""/ep-\d+"""), "/ep-$epParam")
            } else {
                base
            }
            queryParams.add("url=${URLEncoder.encode(adjustedUrl, "UTF-8")}")
        } else if (isAnikotoSlug(clean)) {
            val watchUrl = "https://anikoto.cz/watch/$clean/ep-$epParam"
            queryParams.add("url=${URLEncoder.encode(watchUrl, "UTF-8")}")
            queryParams.add("animeId=${URLEncoder.encode(clean, "UTF-8")}")
        } else {
            queryParams.add("keyword=${URLEncoder.encode(clean, "UTF-8")}")
        }
        queryParams.add("ep=$epParam")
        queryParams.add("type=$type")
        if (!server.isNullOrBlank()) {
            queryParams.add("server=${URLEncoder.encode(server.trim(), "UTF-8")}")
        }
        return "$API_BASE_URL/api/stream/get?${queryParams.joinToString("&")}"
    }

    private fun sanitizeSearchTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("/watch/")) {
            return trimmed
        }
        return trimmed
            .removePrefix("anikoto_")
            .removePrefix("movie_")
            .removePrefix("series_")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * 10. REVERSE-ENGINEERED 16-LANGUAGE SUBTITLE TRACK BUILDER
     * Fetches WebVTT subtitles from https://bildder.nekotvcs.top/anime/05e/{id}/subtitle/
     */
    fun buildFullSubtitleTracks(mediaIdOrSlug: String): List<SubtitleTrack> {
        val cleanId = mediaIdOrSlug.filter { it.isLetterOrDigit() }.ifBlank { "178939" }
        val baseUrl = "https://bildder.nekotvcs.top/anime/05e/$cleanId/subtitle"

        return listOf(
            SubtitleTrack(url = "$baseUrl/CR_English_eng.vtt", lang = "en", label = "English", default = true),
            SubtitleTrack(url = "$baseUrl/CR_Spanish_spa.vtt", lang = "es", label = "Spanish (Latin America)", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Portuguese_por.vtt", lang = "pt", label = "Portuguese (Brazil)", default = false),
            SubtitleTrack(url = "$baseUrl/CR_French_fra.vtt", lang = "fr", label = "French", default = false),
            SubtitleTrack(url = "$baseUrl/CR_German_deu.vtt", lang = "de", label = "German", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Italian_ita.vtt", lang = "it", label = "Italian", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Russian_rus.vtt", lang = "ru", label = "Russian", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Arabic_ara.vtt", lang = "ar", label = "Arabic", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Hindi_hin.vtt", lang = "hi", label = "Hindi", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Malay_may.vtt", lang = "ms", label = "Malay", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Thai_tha.vtt", lang = "th", label = "Thai", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Vietnamese_vie.vtt", lang = "vi", label = "Vietnamese", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Indonesian_ind.vtt", lang = "id", label = "Indonesian", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Chinese_Simplified_chi.vtt", lang = "zh", label = "Chinese (Simplified)", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Chinese_Traditional_zho.vtt", lang = "zh-TW", label = "Chinese (Traditional)", default = false),
            SubtitleTrack(url = "$baseUrl/CR_Polish_pol.vtt", lang = "pl", label = "Polish", default = false)
        )
    }

    private fun parseSubtitles(tracksArray: JSONArray?): List<SubtitleTrack> {
        val list = mutableListOf<SubtitleTrack>()
        if (tracksArray == null) return list
        for (i in 0 until tracksArray.length()) {
            val trackObj = tracksArray.optJSONObject(i) ?: continue
            val file = trackObj.optString("file", "")
            val label = trackObj.optString("label", "English")
            val isDefault = trackObj.optBoolean("default", false)

            if (file.isNotBlank()) {
                val fullSubtitleUrl = makeAbsoluteUrl(file)
                val langCode = when {
                    label.contains("Eng", ignoreCase = true) -> "en"
                    label.contains("Spa", ignoreCase = true) -> "es"
                    label.contains("Ara", ignoreCase = true) -> "ar"
                    label.contains("Fre", ignoreCase = true) || label.contains("Fra", ignoreCase = true) -> "fr"
                    label.contains("Ger", ignoreCase = true) || label.contains("Deu", ignoreCase = true) -> "de"
                    label.contains("Ita", ignoreCase = true) -> "it"
                    label.contains("Por", ignoreCase = true) -> "pt"
                    label.contains("Rus", ignoreCase = true) -> "ru"
                    label.contains("Jap", ignoreCase = true) || label.contains("Jpn", ignoreCase = true) -> "ja"
                    label.contains("Ben", ignoreCase = true) || label.contains("Bangla", ignoreCase = true) -> "bn"
                    label.contains("Hin", ignoreCase = true) -> "hi"
                    label.contains("Kor", ignoreCase = true) -> "ko"
                    label.contains("Chi", ignoreCase = true) || label.contains("Zho", ignoreCase = true) -> "zh"
                    label.contains("Vie", ignoreCase = true) -> "vi"
                    label.contains("Ind", ignoreCase = true) -> "id"
                    label.contains("Tur", ignoreCase = true) -> "tr"
                    label.contains("Tha", ignoreCase = true) -> "th"
                    label.contains("Pol", ignoreCase = true) -> "pl"
                    label.contains("May", ignoreCase = true) || label.contains("Malay", ignoreCase = true) -> "ms"
                    else -> "en"
                }
                list.add(
                    SubtitleTrack(
                        url = fullSubtitleUrl,
                        lang = langCode,
                        label = label,
                        default = isDefault
                    )
                )
            }
        }
        return list
    }

    fun extractServerEmbedUrl(linkId: String, referer: String): String? {
        return if (linkId.isNotBlank()) makeAbsoluteUrl(linkId) else null
    }

    suspend fun extractM3u8AndSubtitlesFromEmbed(embedUrl: String): AnikotoStreamResult? = withContext(Dispatchers.IO) {
        val absUrl = makeAbsoluteUrl(embedUrl)
        AnikotoStreamResult(
            streamUrl = absUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_UA,
                "Referer" to "https://anikoto.cz/",
                "Origin" to "https://anikoto.cz"
            ),
            referer = "https://anikoto.cz/",
            subtitles = buildFullSubtitleTracks("178939").map {
                AnikotoSubtitle(it.url, it.lang, it.label, it.default)
            },
            serverName = "Vidstream-2",
            isM3u8 = true
        )
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    fun buildMediaItem(streamResult: AnikotoStreamResult): MediaItem {
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(streamResult.streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)

        if (streamResult.subtitles.isNotEmpty()) {
            val subtitleConfigs = streamResult.subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(sub.lang)
                    .setLabel(sub.label)
                    .setSelectionFlags(if (sub.default) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        return mediaItemBuilder.build()
    }
}
