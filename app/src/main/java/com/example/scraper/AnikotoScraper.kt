package com.example.scraper

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.example.network.DiagnosticLoggingInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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
    val watchUrl: String
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
    val type: String // "sub", "dub", "hsub"
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
    private const val BASE_URL = "https://anikoto.cz"
    private const val AJAX_URL = "https://anikoto.cz/ajax"
    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * OkHttpClient equipped with diagnostic LoggingInterceptor to inspect all headers (Referer, UA, Origin)
     * and bypass 403 Forbidden / Cloudflare anti-bot blocks.
     */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(DiagnosticLoggingInterceptor(tag = "Anikoto-Scraper-Diagnostic", enforceBypassHeaders = true))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Helper to fetch HTML through the diagnostic OkHttpClient with Referer & User-Agent
     */
    private fun fetchHtml(url: String, referer: String = "$BASE_URL/"): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", referer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtml failed for $url: ${e.message}")
            ""
        }
    }

    /**
     * 1. Search & Discovery: Filter / Search / Browse Anime from Anikoto
     * Routes:
     * - Search / Keyword: GET https://anikoto.cz/filter?keyword={query}
     * - Filter parameters: genre, season, year, type, status, language, rating, sort_by, page
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
        try {
            val queryParams = mutableListOf<String>()
            if (!keyword.isNullOrBlank()) {
                queryParams.add("keyword=${URLEncoder.encode(keyword.trim(), "UTF-8")}")
            }
            if (!genre.isNullOrBlank()) queryParams.add("genre=${URLEncoder.encode(genre.trim(), "UTF-8")}")
            if (!season.isNullOrBlank()) queryParams.add("season=${URLEncoder.encode(season.trim(), "UTF-8")}")
            if (!year.isNullOrBlank()) queryParams.add("year=${URLEncoder.encode(year.trim(), "UTF-8")}")
            if (!type.isNullOrBlank()) queryParams.add("type=${URLEncoder.encode(type.trim(), "UTF-8")}")
            if (!status.isNullOrBlank()) queryParams.add("status=${URLEncoder.encode(status.trim(), "UTF-8")}")
            if (!language.isNullOrBlank()) queryParams.add("language=${URLEncoder.encode(language.trim(), "UTF-8")}")
            if (!rating.isNullOrBlank()) queryParams.add("rating=${URLEncoder.encode(rating.trim(), "UTF-8")}")
            if (!sortBy.isNullOrBlank()) {
                queryParams.add("sort_by=${URLEncoder.encode(sortBy.trim(), "UTF-8")}")
            } else if (keyword.isNullOrBlank()) {
                queryParams.add("sort_by=latest-updated")
            }
            if (page > 1) queryParams.add("page=$page")

            val url = "$BASE_URL/filter?${queryParams.joinToString("&")}"
            Log.d(TAG, "Fetching Anikoto catalog via OkHttp: $url")
            val html = fetchHtml(url, referer = "$BASE_URL/")
            if (html.isBlank()) return@withContext emptyList()

            val doc = Jsoup.parse(html, BASE_URL)
            val animeElements = doc.select("#list-items .item, .ani.items .item, .film_list-wrap .flw-item, .film_list .flw-item, .film_list-wrap .item, .flw-item")
            
            for (element in animeElements) {
                try {
                    val linkEl = element.selectFirst(".ani.poster a, a.name.d-title, a.film-poster-ahref, a[href*='/watch/'], a.dynamic-name")
                    val title = element.selectFirst(".name.d-title, .film-name a, .dynamic-name, .title, h3.film-name")?.text()?.trim()
                        ?: element.selectFirst("img")?.attr("alt")?.trim()
                        ?: linkEl?.attr("title")?.trim()
                        ?: ""

                    if (title.isBlank()) continue

                    val relativeHref = linkEl?.attr("href") ?: ""
                    val watchUrl = if (relativeHref.startsWith("http")) relativeHref else "$BASE_URL$relativeHref"

                    val imgEl = element.selectFirst(".ani.poster img, img.film-poster-img, .film-poster img, img")
                    var posterUrl = imgEl?.attr("src")?.takeIf { it.isNotBlank() }
                        ?: imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                        ?: ""
                    if (posterUrl.startsWith("//")) {
                        posterUrl = "https:$posterUrl"
                    } else if (posterUrl.startsWith("/") && !posterUrl.startsWith("//")) {
                        posterUrl = "$BASE_URL$posterUrl"
                    }

                    val typeText = element.selectFirst(".meta .right, .fdi-item, .tick-item.tick-type, .fdi-item.fdi-type")?.text()?.trim() ?: "TV"
                    val subCount = element.selectFirst(".ep-status.sub, .tick-sub, .tick-item.tick-sub")?.text()?.trim() ?: ""
                    val dubCount = element.selectFirst(".ep-status.dub, .tick-dub, .tick-item.tick-dub")?.text()?.trim() ?: ""
                    val totalCount = element.selectFirst(".ep-status.total, .tick-eps, .tick-item.tick-eps")?.text()?.trim() ?: ""
                    val desc = element.selectFirst(".description, .film-description, .text-dimmed")?.text()?.trim() ?: ""
                    val rating = element.selectFirst(".tick-item.tick-rate, .tick-rate, .tick-imdb")?.text()?.trim() ?: ""
                    val releaseYear = element.selectFirst(".fdi-item.fdi-duration, .release-year, .year")?.text()?.trim() ?: ""

                    val id = extractSlugId(watchUrl).ifEmpty {
                        title.lowercase().replace(Regex("[^a-z0-9]"), "-")
                    }

                    results.add(
                        AnikotoAnimeItem(
                            id = id,
                            title = title,
                            posterUrl = posterUrl,
                            watchUrl = watchUrl,
                            type = typeText,
                            episodesInfo = if (totalCount.isNotEmpty()) "Ep $totalCount" else if (subCount.isNotEmpty()) "Ep $subCount" else "Full",
                            rating = rating,
                            subCount = subCount,
                            dubCount = dubCount,
                            description = desc,
                            releaseYear = releaseYear
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed parsing single anime element: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchOrFilterAnime error: ${e.message}", e)
        }
        results.distinctBy { it.watchUrl }
    }

    /**
     * Live Suggestion API: GET https://anikoto.cz/ajax/search/suggest?keyword={query}
     */
    suspend fun getLiveSuggestions(keyword: String): List<AnikotoAnimeItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnikotoAnimeItem>()
        if (keyword.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
            val url = "$AJAX_URL/search/suggest?keyword=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$BASE_URL/")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            var resultHtml = body
            if (body.startsWith("{")) {
                val json = JSONObject(body)
                resultHtml = json.optString("result", json.optString("html", body))
            }

            if (resultHtml.isNotBlank()) {
                val doc = Jsoup.parse(resultHtml, BASE_URL)
                val items = doc.select(".nav-item, .item, a[href*='/watch/'], a.ss-item")
                for (el in items) {
                    val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href*='/watch/']")
                    val relativeHref = linkEl?.attr("href") ?: ""
                    if (relativeHref.isBlank()) continue
                    val watchUrl = if (relativeHref.startsWith("http")) relativeHref else "$BASE_URL$relativeHref"

                    val title = el.selectFirst(".film-name, .name, .d-title, .title")?.text()?.trim()
                        ?: linkEl?.attr("title")?.trim()
                        ?: el.text().trim()
                    if (title.isBlank()) continue

                    val imgEl = el.selectFirst("img")
                    var posterUrl = imgEl?.attr("src")?.takeIf { it.isNotBlank() }
                        ?: imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                        ?: ""
                    if (posterUrl.startsWith("//")) {
                        posterUrl = "https:$posterUrl"
                    } else if (posterUrl.startsWith("/") && !posterUrl.startsWith("//")) {
                        posterUrl = "$BASE_URL$posterUrl"
                    }

                    val id = extractSlugId(watchUrl).ifEmpty {
                        title.lowercase().replace(Regex("[^a-z0-9]"), "-")
                    }

                    results.add(
                        AnikotoAnimeItem(
                            id = id,
                            title = title,
                            posterUrl = posterUrl,
                            watchUrl = watchUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getLiveSuggestions error: ${e.message}")
        }
        results.distinctBy { it.watchUrl }
    }

    /**
     * Season Extraction API
     */
    suspend fun fetchSeasons(watchUrlOrSlug: String): List<AnikotoSeason> = withContext(Dispatchers.IO) {
        val seasons = mutableListOf<AnikotoSeason>()
        try {
            var fullUrl = watchUrlOrSlug
            if (!fullUrl.startsWith("http")) {
                fullUrl = "$BASE_URL/watch/$watchUrlOrSlug"
            }
            val html = fetchHtml(fullUrl, referer = "$BASE_URL/")
            if (html.isBlank()) return@withContext emptyList()

            val doc = Jsoup.parse(html, fullUrl)
            
            // 1. Direct on-page season elements
            val seasonElements = doc.select(".os-list .os-item, .os-list a, .seasons-block a, #seasons a, .dropdown-menu a[href*='/watch/'], #ani-seasons a, #w-related a[href*='/watch/']")
            var count = 1
            for (el in seasonElements) {
                val href = el.attr("href") ?: ""
                if (href.contains("/watch/") && !href.contains("/episode/")) {
                    val title = el.text().trim().ifEmpty { "Season $count" }
                    val fullSeasonUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                    if (seasons.none { it.watchUrl == fullSeasonUrl }) {
                        val numMatch = Regex("""\b(?:Season|S|s|Part|Cour)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(title)
                        val num = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: count
                        seasons.add(
                            AnikotoSeason(
                                number = num,
                                title = title,
                                watchUrl = fullSeasonUrl
                            )
                        )
                        count++
                    }
                }
            }

            // 2. Fetch watch-order via ajax if animeDataId is present
            var animeDataId = doc.selectFirst(".anis-watch-wrap[data-id], #wrapper[data-id], .film_detail[data-id], [data-id]")?.attr("data-id") ?: ""
            if (animeDataId.isBlank()) {
                val match = Regex("""(?:data-id|anime_id|animeId|movie_id|film_id)\s*[:=]\s*["']?(\d+)["']?""").find(html)
                if (match != null) {
                    animeDataId = match.groupValues.getOrNull(1) ?: ""
                }
            }
            if (animeDataId.isNotBlank()) {
                try {
                    val woUrl = "$BASE_URL/api/watch-order/$animeDataId"
                    val request = Request.Builder()
                        .url(woUrl)
                        .header("User-Agent", DEFAULT_UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", fullUrl)
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string() ?: ""
                    var resultHtml = body
                    if (body.startsWith("{")) {
                        val jsObj = JSONObject(body)
                        resultHtml = jsObj.optString("result", jsObj.optString("html", body))
                    }
                    if (resultHtml.isNotBlank()) {
                        val woDoc = Jsoup.parse(resultHtml)
                        val items = woDoc.select(".item a[href*='/watch/']")
                        for (el in items) {
                            val href = el.attr("href") ?: ""
                            if (href.contains("/watch/")) {
                                val fullSeasonUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                                val nameEl = el.selectFirst(".name, .d-title") ?: el
                                val itemTitle = nameEl.text().trim().ifEmpty { el.attr("title").trim() }
                                if (itemTitle.isNotEmpty() && seasons.none { it.watchUrl == fullSeasonUrl }) {
                                    val numMatch = Regex("""\b(?:Season|S|s|Part|Cour)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(itemTitle)
                                    val num = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: (seasons.size + 1)
                                    seasons.add(
                                        AnikotoSeason(
                                            number = num,
                                            title = itemTitle,
                                            watchUrl = fullSeasonUrl
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "watch-order parsing error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSeasons error: ${e.message}", e)
        }
        if (seasons.isEmpty()) {
            seasons.add(AnikotoSeason(number = 1, title = "Season 1", watchUrl = watchUrlOrSlug))
        }
        seasons.distinctBy { it.number }.sortedBy { it.number }
    }

    /**
     * Detail Extraction API
     */
    suspend fun fetchAnimeDetails(watchUrlOrSlug: String): AnikotoDetails? = withContext(Dispatchers.IO) {
        try {
            var fullUrl = watchUrlOrSlug
            if (!fullUrl.startsWith("http")) {
                fullUrl = "$BASE_URL/watch/$watchUrlOrSlug"
            }
            val html = fetchHtml(fullUrl, referer = "$BASE_URL/")
            if (html.isBlank()) return@withContext null

            val doc = Jsoup.parse(html, fullUrl)
            val title = doc.selectFirst(".name.d-title, .film-name, h2.title, h1")?.text()?.trim() ?: ""
            
            // Prefer itemprop="image" or thumbnail images from CDN (never logo.png)
            val imgEl = doc.selectFirst("img[itemprop='image'], .ani.poster img, .film-poster img, .poster img[src*='thumbnail'], img[src*='anipixcdn.co'], img[src*='thumbnail']")
                ?: doc.select("img").firstOrNull { 
                    val src = it.attr("src")
                    !src.contains("logo", ignoreCase = true) && (src.contains("http") || src.startsWith("/"))
                }
            var posterUrl = imgEl?.attr("src")?.takeIf { it.isNotBlank() }
                ?: imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: ""
            if (posterUrl.startsWith("//")) {
                posterUrl = "https:$posterUrl"
            } else if (posterUrl.startsWith("/") && !posterUrl.startsWith("//")) {
                posterUrl = "$BASE_URL$posterUrl"
            }

            val description = doc.selectFirst(".description, .film-description, .text-dimmed")?.text()?.trim() ?: ""
            val rating = doc.selectFirst(".rating, .imdb-rating, .tick-item.tick-imdb")?.text()?.trim() ?: ""
            val releaseYear = doc.selectFirst(".year, .release-year")?.text()?.trim() ?: ""

            val seasons = fetchSeasons(fullUrl)
            val episodes = fetchEpisodes(fullUrl)

            return@withContext AnikotoDetails(
                title = title,
                posterUrl = posterUrl,
                description = description,
                rating = rating,
                releaseYear = releaseYear,
                totalSeasons = seasons.size,
                totalEpisodes = episodes.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchAnimeDetails error: ${e.message}", e)
        }
        null
    }

    /**
     * 2. Episode Extraction API
     * Pipeline:
     * - Checks internal anime ID from DOM or slug (e.g. 'clxsl', 'lh0li', 'gqc')
     * - Hits GET https://anikoto.cz/ajax/episode/list/{anime_internal_id}
     * - Parses each episode with episode_id (data-id) and data-ids token
     */
    suspend fun fetchEpisodes(watchUrlOrSlug: String): List<AnikotoEpisode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<AnikotoEpisode>()
        try {
            var fullUrl = watchUrlOrSlug
            if (!fullUrl.startsWith("http")) {
                fullUrl = "$BASE_URL/watch/$watchUrlOrSlug"
            }

            Log.d(TAG, "Fetching episode list for: $fullUrl (Referer: $fullUrl)")
            val html = fetchHtml(fullUrl, referer = "$BASE_URL/")
            if (html.isBlank()) return@withContext emptyList()

            val doc = Jsoup.parse(html, fullUrl)

            // 1. Get internal anime ID from DOM or URL slug
            var animeDataId = doc.selectFirst(".anis-watch-wrap[data-id], #wrapper[data-id], .film_detail[data-id], [data-id]")?.attr("data-id") ?: ""
            if (animeDataId.isBlank() || animeDataId.length > 10) {
                // Try extracting id from slug e.g. solo-leveling-lh0li/ep-1 -> lh0li, or naruto-674 -> 674
                val cleanPath = fullUrl.substringBefore("/ep-").substringBefore("?").trimEnd('/')
                val lastDash = cleanPath.substringAfterLast("-")
                if (lastDash.isNotBlank() && lastDash.length in 2..8) {
                    animeDataId = lastDash
                }
            }
            if (animeDataId.isBlank()) {
                val scriptText = doc.html()
                val match = Regex("""(?:data-id|anime_id|animeId|movie_id|film_id)\s*[:=]\s*["']?([a-zA-Z0-9_-]+)["']?""").find(scriptText)
                if (match != null) {
                    animeDataId = match.groupValues.getOrNull(1) ?: ""
                }
            }

            val ajaxUrls = mutableListOf<String>()
            if (animeDataId.isNotBlank()) {
                ajaxUrls.add("$AJAX_URL/episode/list/$animeDataId")
                ajaxUrls.add("$AJAX_URL/panel?id=$animeDataId")
            }

            for (ajaxUrl in ajaxUrls) {
                try {
                    val request = Request.Builder()
                        .url(ajaxUrl)
                        .header("User-Agent", DEFAULT_UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", fullUrl)
                        .build()

                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string() ?: ""
                    var resultHtml = body
                    if (body.startsWith("{")) {
                        val jsObj = JSONObject(body)
                        resultHtml = jsObj.optString("result", jsObj.optString("html", body))
                    }

                    if (resultHtml.isNotBlank()) {
                        val epDoc = Jsoup.parse(resultHtml)
                        val epElements = epDoc.select(".episodes.name a, .ep-range a, a[data-id], a[data-ids], a.ep-item, li[title] a, .episodes a")
                        for (epEl in epElements) {
                            val dataId = epEl.attr("data-id").ifEmpty { epEl.attr("data-number") }
                            val dataNum = epEl.attr("data-num").ifEmpty { epEl.attr("data-slug") }
                            val num = dataNum.toIntOrNull() ?: (episodes.size + 1)
                            val title = epEl.attr("title").ifEmpty { 
                                epEl.parent()?.attr("title") ?: epEl.selectFirst(".d-title")?.text() ?: epEl.text().trim().ifEmpty { "Episode $num" }
                            }
                            val dataIds = epEl.attr("data-ids").ifEmpty { dataId }

                            if (episodes.none { it.number == num }) {
                                episodes.add(
                                    AnikotoEpisode(
                                        id = dataId,
                                        number = num,
                                        title = title,
                                        dataIdsToken = dataIds
                                    )
                                )
                            }
                        }
                    }
                    if (episodes.isNotEmpty()) break
                } catch (e: Exception) {
                    Log.w(TAG, "ajax episode list attempt failed for $ajaxUrl: ${e.message}")
                }
            }

            // Fallback: parse direct on-page episode links if ajax was empty
            if (episodes.isEmpty()) {
                val onPageEps = doc.select(".episodes.name a, .ep-range a, a[data-id], a[data-ids], .ss-list a, .ep-item a, a[href*='/watch/']")
                for (epEl in onPageEps) {
                    val href = epEl.attr("href")
                    if (href.contains("/ep-") || epEl.hasAttr("data-id")) {
                        val dataId = epEl.attr("data-id").ifEmpty { epEl.attr("data-number") }
                        val numMatch = Regex("""/ep-(\d+)""").find(href)
                        val num = numMatch?.groupValues?.get(1)?.toIntOrNull()
                            ?: epEl.attr("data-num").toIntOrNull()
                            ?: (episodes.size + 1)
                        val title = epEl.attr("title").ifEmpty { epEl.text().trim().ifEmpty { "Episode $num" } }
                        val dataIds = epEl.attr("data-ids").ifEmpty { dataId }
                        if (episodes.none { it.number == num }) {
                            episodes.add(
                                AnikotoEpisode(
                                    id = dataId,
                                    number = num,
                                    title = title,
                                    dataIdsToken = dataIds
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchEpisodes error: ${e.message}", e)
        }

        if (episodes.isEmpty()) {
            episodes.add(AnikotoEpisode(id = "1", number = 1, title = "Episode 1"))
        }

        episodes.distinctBy { it.number }.sortedBy { it.number }
    }

    /**
     * 3. Server & Sub/Dub Sources Extraction API
     * Routes:
     * - GET https://anikoto.cz/ajax/episode/servers?episodeId={episode_id}
     * - GET https://anikoto.cz/ajax/server/list?servers={dataIdsToken}
     */
    suspend fun fetchServers(dataIdsToken: String, refererUrl: String = BASE_URL): List<AnikotoServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<AnikotoServer>()
        if (dataIdsToken.isBlank()) return@withContext servers
        try {
            val urls = listOf(
                "$AJAX_URL/episode/servers?episodeId=${URLEncoder.encode(dataIdsToken, "UTF-8")}",
                "$AJAX_URL/server/list?servers=${URLEncoder.encode(dataIdsToken, "UTF-8")}"
            )

            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", DEFAULT_UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", refererUrl)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    var resultHtml = body
                    if (body.startsWith("{")) {
                        val jsObj = JSONObject(body)
                        resultHtml = jsObj.optString("result", jsObj.optString("html", body))
                    }

                    if (resultHtml.isNotBlank()) {
                        val doc = Jsoup.parse(resultHtml)
                        val serverItems = doc.select(".servers li[data-link-id], .server-item[data-link-id], .ps__-list .item, [data-link-id], [data-id], .btn-server")
                        for (item in serverItems) {
                            val linkId = item.attr("data-link-id").ifEmpty { item.attr("data-id") }.trim()
                            var name = item.text().trim().ifEmpty { "Server" }
                            val parentWithType = item.parents().firstOrNull { it.hasAttr("data-type") }
                            val type = parentWithType?.attr("data-type")
                                ?: item.attr("data-type").takeIf { it.isNotBlank() }
                                ?: if (item.parents().any { it.className().contains("dub", ignoreCase = true) || it.id().contains("dub", ignoreCase = true) }) "dub" else "sub"
                            val svId = item.attr("data-sv-id").ifEmpty { item.attr("data-server-id") }.trim()

                            if (name == "-1" || name.equals("null", ignoreCase = true) || name.contains("vid", ignoreCase = true) || name.contains("stream", ignoreCase = true) || name.contains("play", ignoreCase = true)) {
                                name = "Server"
                            }

                            if (linkId.isNotBlank() && linkId != "-1" && !linkId.equals("null", ignoreCase = true)) {
                                servers.add(
                                    AnikotoServer(
                                        id = svId,
                                        linkId = linkId,
                                        name = name,
                                        type = type
                                    )
                                )
                            }
                        }
                    }
                    if (servers.isNotEmpty()) break
                } catch (e: Exception) {
                    Log.w(TAG, "fetchServers attempt failed for $url: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchServers error: ${e.message}")
        }
        servers
    }

    /**
     * 4. Embed Iframe / Video Source Decryption API
     * Routes:
     * - GET https://anikoto.cz/ajax/episode/sources?id={server_data_id}
     * - GET https://anikoto.cz/ajax/server?get={linkId}
     * Response: {"type": "iframe", "link": "https://megacloud.tv/embed-2/e-1/{hash}?z=", "server": 1}
     */
    suspend fun extractServerEmbedUrl(linkId: String, refererUrl: String = BASE_URL): String? = withContext(Dispatchers.IO) {
        try {
            val encodedId = URLEncoder.encode(linkId, "UTF-8")
            val urls = listOf(
                "$AJAX_URL/episode/sources?id=$encodedId",
                "$AJAX_URL/server?get=$encodedId",
                "$BASE_URL/ajax/v2/episode/sources?id=$encodedId"
            )

            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", DEFAULT_UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", refererUrl)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val resultObj = json.optJSONObject("result")
                        val embedUrl = resultObj?.optString("link", resultObj.optString("url", "")) 
                            ?: json.optString("link", json.optString("url", ""))
                        if (embedUrl.isNotBlank()) {
                            return@withContext embedUrl
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "extractServerEmbedUrl attempt failed for $url: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractServerEmbedUrl error: ${e.message}")
        }
        null
    }

    /**
     * 5. Deep Master M3U8 & Subtitles Extractor from Embed Player
     */
    suspend fun extractM3u8AndSubtitlesFromEmbed(embedUrl: String): AnikotoStreamResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deep Scraping Embed Player: $embedUrl")
            val embedUri = Uri.parse(embedUrl)
            val host = embedUri.host ?: "megaplay.buzz"
            val embedOrigin = "${embedUri.scheme ?: "https"}://$host"

            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", BASE_URL)
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""

            var m3u8Url: String? = null
            val subtitles = mutableListOf<AnikotoSubtitle>()

            // 1. Check data-id / data-realid inside embed HTML
            val doc = Jsoup.parse(html)
            val playerEl = doc.selectFirst("#megaplay-player, [data-id], .fix-area")
            val embedDataId = playerEl?.attr("data-id")?.takeIf { it.isNotBlank() }
                ?: playerEl?.attr("data-realid")?.takeIf { it.isNotBlank() }
                ?: embedUri.lastPathSegment?.substringBefore("?") ?: ""

            // 2. Query stream/getSources endpoint
            if (embedDataId.isNotBlank()) {
                val sourceEndpoints = listOf(
                    "$embedOrigin/stream/getSources?id=$embedDataId",
                    "$embedOrigin/stream/getSourcesNew?id=$embedDataId",
                    "$embedOrigin/embed-2/ajax/e-1/getSources?id=$embedDataId"
                )

                for (sourceUrl in sourceEndpoints) {
                    try {
                        val srcReq = Request.Builder()
                            .url(sourceUrl)
                            .header("User-Agent", DEFAULT_UA)
                            .header("Referer", embedUrl)
                            .header("Origin", embedOrigin)
                            .header("X-Requested-With", "XMLHttpRequest")
                            .build()

                        val srcRes = httpClient.newCall(srcReq).execute()
                        val srcBody = srcRes.body?.string() ?: ""
                        if (srcRes.isSuccessful && srcBody.startsWith("{")) {
                            val json = JSONObject(srcBody)
                            
                            // Check sources object or array
                            val sourcesObj = json.optJSONObject("sources")
                            val fileFromObj = sourcesObj?.optString("file", "")
                            if (!fileFromObj.isNullOrBlank()) {
                                m3u8Url = fileFromObj
                            } else {
                                val sourcesArr = json.optJSONArray("sources")
                                if (sourcesArr != null && sourcesArr.length() > 0) {
                                    val firstItem = sourcesArr.optJSONObject(0)
                                    val fileFromArr = firstItem?.optString("file", "")
                                    if (!fileFromArr.isNullOrBlank()) {
                                        m3u8Url = fileFromArr
                                    }
                                }
                            }

                            // Extract subtitle tracks
                            val tracksArr = json.optJSONArray("tracks")
                            if (tracksArr != null) {
                                for (i in 0 until tracksArr.length()) {
                                    val track = tracksArr.optJSONObject(i) ?: continue
                                    val file = track.optString("file", "")
                                    val label = track.optString("label", "English")
                                    val kind = track.optString("kind", "")
                                    if (file.isNotBlank() && (kind == "captions" || file.endsWith(".vtt") || file.endsWith(".srt") || kind.isEmpty())) {
                                        subtitles.add(
                                            AnikotoSubtitle(
                                                url = file,
                                                lang = track.optString("lang", label.take(2).lowercase()),
                                                label = label,
                                                default = track.optBoolean("default", false)
                                            )
                                        )
                                    }
                                }
                            }

                            if (!m3u8Url.isNullOrBlank()) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Source endpoint attempt failed for $sourceUrl: ${e.message}")
                    }
                }
            }

            // 3. Fallback Regex Parsing if not resolved
            if (m3u8Url.isNullOrBlank()) {
                val m3u8Regexes = listOf(
                    """(?:file|source|src)\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""".toRegex(),
                    """["'](https?://[^"']+\.m3u8[^"']*)["']""".toRegex(),
                    """https?://[a-zA-Z0-9.\-_/:]+\.m3u8[a-zA-Z0-9._\-%&=?]*""".toRegex()
                )

                for (regex in m3u8Regexes) {
                    val match = regex.find(html)
                    if (match != null) {
                        m3u8Url = match.groupValues.getOrNull(1) ?: match.value
                        if (m3u8Url.isNotBlank()) break
                    }
                }
            }

            if (!m3u8Url.isNullOrBlank()) {
                Log.d(TAG, "Successfully extracted master M3U8: $m3u8Url")
                val streamHeaders = mapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to "$embedOrigin/",
                    "Origin" to embedOrigin
                )

                return@withContext AnikotoStreamResult(
                    streamUrl = m3u8Url,
                    headers = streamHeaders,
                    referer = "$embedOrigin/",
                    subtitles = subtitles
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractM3u8AndSubtitlesFromEmbed error: ${e.message}")
        }
        null
    }

    /**
     * 6. High-Level Universal Extractor
     */
    suspend fun fetchAvailableServers(
        title: String,
        season: Int = 1,
        episode: Int = 1
    ): AnikotoServerGroup = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching available SUB / DUB servers for: $title (Ep $episode)")
            val matchedWatchUrl = if (title.startsWith("http") || title.contains("anikoto.cz") || title.contains("/watch/")) {
                title
            } else {
                var searchResults = searchOrFilterAnime(keyword = title)
                
                // Fallback 1: If empty, try splitting by typical punctuation and search first part
                if (searchResults.isEmpty()) {
                    val splitTitle = title.split(Regex("[:\\-–—]")).first().trim()
                    if (splitTitle.isNotBlank() && splitTitle != title) {
                        Log.d(TAG, "Fallback 1: Searching Anikoto with split prefix: $splitTitle")
                        searchResults = searchOrFilterAnime(keyword = splitTitle)
                    }
                }
                
                // Fallback 2: Try first 3 words
                if (searchResults.isEmpty()) {
                    val words = title.split(" ")
                    if (words.size > 3) {
                        val firstWords = words.take(3).joinToString(" ").trim()
                        Log.d(TAG, "Fallback 2: Searching Anikoto with first 3 words: $firstWords")
                        searchResults = searchOrFilterAnime(keyword = firstWords)
                    }
                }

                // Fallback 3: Try removing apostrophes and quotes
                if (searchResults.isEmpty()) {
                    val cleanTitle = title.replace(Regex("['’\"‘]"), "").trim()
                    if (cleanTitle != title) {
                        Log.d(TAG, "Fallback 3: Searching Anikoto with clean title: $cleanTitle")
                        searchResults = searchOrFilterAnime(keyword = cleanTitle)
                    }
                }

                // Fallback 4: Try first 2 words
                if (searchResults.isEmpty()) {
                    val words = title.split(" ")
                    if (words.size > 2) {
                        val firstTwoWords = words.take(2).joinToString(" ").trim()
                        Log.d(TAG, "Fallback 4: Searching Anikoto with first 2 words: $firstTwoWords")
                        searchResults = searchOrFilterAnime(keyword = firstTwoWords)
                    }
                }

                if (searchResults.isEmpty()) {
                    Log.w(TAG, "No search results found on Anikoto for: $title after all fallbacks")
                    return@withContext AnikotoServerGroup(emptyList(), emptyList(), "", episode)
                }

                val cleanTarget = title.lowercase().replace(Regex("[^a-z0-9]"), "")
                val matchedAnime = searchResults.minByOrNull { item ->
                    val cleanItem = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                    when {
                        cleanItem == cleanTarget -> 0
                        cleanItem == "${cleanTarget}tv" || cleanItem == "tv${cleanTarget}" -> 1
                        item.type.equals("tv", ignoreCase = true) && cleanItem.startsWith(cleanTarget) && !cleanItem.contains("road") && !cleanItem.contains("ova") && !cleanItem.contains("special") -> 2
                        cleanTarget.startsWith(cleanItem) && !cleanItem.contains("road") && !cleanItem.contains("ova") -> 3
                        cleanItem.startsWith(cleanTarget) && !cleanItem.contains("road") && !cleanItem.contains("ova") && !cleanItem.contains("special") -> 5
                        cleanTarget.contains(cleanItem) && !cleanItem.contains("road") && !cleanItem.contains("ova") -> 6
                        cleanItem.contains(cleanTarget) && !cleanItem.contains("road") && !cleanItem.contains("ova") -> 8
                        else -> 100
                    }
                } ?: searchResults.first()
                matchedAnime.watchUrl
            }

            val seasons = fetchSeasons(matchedWatchUrl)
            val seasonWatchUrl = seasons.find { it.number == season }?.watchUrl ?: matchedWatchUrl

            val episodes = fetchEpisodes(seasonWatchUrl)
            val targetEp = episodes.firstOrNull { it.number == episode }
                ?: episodes.firstOrNull()
                ?: return@withContext AnikotoServerGroup(emptyList(), emptyList(), seasonWatchUrl, episode)

            val allServers = fetchServers(targetEp.dataIdsToken, seasonWatchUrl)
            
            // Clean server names and number them cleanly (Server 1, Server 2, Server 3)
            val subServers = allServers.filter {
                val t = it.type.lowercase()
                t == "sub" || t == "hsub"
            }.distinctBy { it.linkId }.mapIndexed { idx, srv ->
                srv.copy(name = "Server ${idx + 1}")
            }

            val dubServers = allServers.filter {
                it.type.lowercase() == "dub"
            }.distinctBy { it.linkId }.mapIndexed { idx, srv ->
                srv.copy(name = "Server ${idx + 1}")
            }

            Log.d(TAG, "Fetched ${subServers.size} SUB servers and ${dubServers.size} DUB servers for $title")
            return@withContext AnikotoServerGroup(
                subServers = subServers,
                dubServers = dubServers,
                watchUrl = seasonWatchUrl,
                episodeNum = targetEp.number
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchAvailableServers error: ${e.message}", e)
            AnikotoServerGroup(emptyList(), emptyList(), "", episode)
        }
    }

    suspend fun extractStreamFromServer(
        server: AnikotoServer,
        watchUrl: String = BASE_URL
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val fullWatchUrl = if (watchUrl.isNotBlank()) watchUrl else BASE_URL
            val embedUrl = extractServerEmbedUrl(server.linkId, fullWatchUrl)
            if (!embedUrl.isNullOrBlank()) {
                Log.d(TAG, "Extracting stream from server [${server.name}] embed: $embedUrl")
                val streamResult = extractM3u8AndSubtitlesFromEmbed(embedUrl)
                if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                    return@withContext ScrapedStreamResult(
                        streamUrl = streamResult.streamUrl,
                        headers = streamResult.headers,
                        referer = streamResult.referer
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractStreamFromServer error: ${e.message}", e)
        }
        null
    }

    suspend fun getStreamByTitle(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        preferDub: Boolean = false
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initiating Anikoto deep stream search for anime: $title (S$season Ep $episode)")
            
            val matchedWatchUrl = if (title.startsWith("http") || title.contains("anikoto.cz") || title.contains("/watch/")) {
                title
            } else {
                // Step 1: Search Anikoto
                val searchResults = searchOrFilterAnime(keyword = title)
                if (searchResults.isEmpty()) {
                    Log.w(TAG, "No search results found on Anikoto for: $title")
                    return@withContext null
                }

                val cleanTarget = title.lowercase().replace(Regex("[^a-z0-9]"), "")
                val matchedAnime = searchResults.minByOrNull { item ->
                    val cleanItem = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                    when {
                        cleanItem == cleanTarget -> 0
                        cleanItem == "${cleanTarget}tv" || cleanItem == "tv${cleanTarget}" -> 1
                        item.type.equals("tv", ignoreCase = true) && cleanItem.startsWith(cleanTarget) && !cleanItem.contains("road") && !cleanItem.contains("ova") && !cleanItem.contains("special") -> 2
                        cleanItem.startsWith(cleanTarget) && !cleanItem.contains("road") && !cleanItem.contains("ova") && !cleanItem.contains("special") -> 5
                        cleanItem.contains(cleanTarget) && !cleanItem.contains("road") && !cleanItem.contains("ova") -> 10
                        else -> 100
                    }
                } ?: searchResults.first()

                Log.d(TAG, "Matched Anikoto anime: ${matchedAnime.title} (${matchedAnime.watchUrl})")
                matchedAnime.watchUrl
            }

            // Step 2: Fetch season watch URL
            val seasons = fetchSeasons(matchedWatchUrl)
            val seasonWatchUrl = seasons.find { it.number == season }?.watchUrl ?: matchedWatchUrl

            // Step 3: Fetch episodes
            val episodes = fetchEpisodes(seasonWatchUrl)
            val targetEp = episodes.firstOrNull { it.number == episode }
                ?: episodes.firstOrNull()
                ?: return@withContext null

            Log.d(TAG, "Selected Episode: #${targetEp.number} (token: ${targetEp.dataIdsToken.take(15)}...)")

            // Step 4: Fetch servers
            val servers = fetchServers(targetEp.dataIdsToken, seasonWatchUrl)
            if (servers.isEmpty()) {
                Log.w(TAG, "No streaming servers found for episode ${targetEp.number}")
                return@withContext null
            }

            // Step 5: Pick server and extract embed URL
            for (srv in servers) {
                try {
                    val embedUrl = extractServerEmbedUrl(srv.linkId, seasonWatchUrl)
                    if (!embedUrl.isNullOrBlank()) {
                        Log.d(TAG, "Extracted Embed URL: $embedUrl from ${srv.name}")
                        val streamResult = extractM3u8AndSubtitlesFromEmbed(embedUrl)
                        if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                            return@withContext ScrapedStreamResult(
                                streamUrl = streamResult.streamUrl,
                                headers = streamResult.headers,
                                referer = streamResult.referer
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed server attempt ${srv.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getStreamByTitle failed: ${e.message}", e)
        }
        null
    }

    /**
     * Builds a Media3 MediaItem with all scraped subtitle tracks configured
     */
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

    private fun extractSlugId(url: String): String {
        return try {
            val path = Uri.parse(url).path ?: ""
            val segments = path.split("/").filter { it.isNotBlank() }
            segments.lastOrNull { it != "ep-1" && it != "ep" } ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
