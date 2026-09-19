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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
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
    val type: String, // "sub", "dub", "hsub"
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
    private const val BASE_URL = "https://anikoto.cz"
    private const val AJAX_URL = "https://anikoto.cz/ajax"
    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // Default AES decryption keys for Megaplay / Kryntal encrypted anime streams
    private const val DEFAULT_MEGAPLAY_AES_KEY = "i?LMTAx0Q6,:}50U"
    private const val DEFAULT_MEGAPLAY_AES_IV = "W0;27ToaUpl_P%'c"

    @Volatile
    private var cachedMegaplayAesKey: String = DEFAULT_MEGAPLAY_AES_KEY
    @Volatile
    private var cachedMegaplayAesIv: String = DEFAULT_MEGAPLAY_AES_IV

    /**
     * Sanitizes poster URL for safe loading across the app
     */
    fun sanitizePosterUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) return ""
        if (trimmed.contains("anilist.co") || trimmed.contains("myanimelist.net") || trimmed.contains("tmdb.org")) {
            return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        }
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> "$BASE_URL$trimmed"
            else -> "$BASE_URL/$trimmed"
        }
    }

    /**
     * Resolves watch URL from a title or slug
     */
    suspend fun resolveAnikotoWatchUrl(titleOrSlug: String): String? = withContext(Dispatchers.IO) {
        val raw = titleOrSlug.trim()
            .removePrefix("anikoto_")
            .removePrefix("movie_")
            .removePrefix("series_")
            .trim()

        if (raw.isBlank()) return@withContext null

        if (raw.startsWith("http://") || raw.startsWith("https://") || raw.contains("/watch/")) {
            return@withContext raw
        }

        val searchItems = searchOrFilterAnime(keyword = raw)
        val matched = searchItems.firstOrNull()
        matched?.watchUrl ?: "$BASE_URL/watch/${raw.lowercase().replace(Regex("[^a-z0-9]"), "-")}"
    }

    /**
     * Decrypts AES-CBC encrypted stream data payload ("enc") from Megaplay / Kryntal / e1-player.
     */
    fun decryptMegaplayEnc(
        enc: String,
        customKey: String? = null,
        customIv: String? = null
    ): String? {
        if (enc.isBlank()) return null
        val keyStr = customKey ?: cachedMegaplayAesKey
        val ivStr = customIv ?: cachedMegaplayAesIv

        return try {
            val keyBytes = ByteArray(32)
            val rawKey = keyStr.toByteArray(Charsets.UTF_8)
            System.arraycopy(rawKey, 0, keyBytes, 0, minOf(32, rawKey.size))

            val ivBytes = ByteArray(16)
            val rawIv = ivStr.toByteArray(Charsets.UTF_8)
            System.arraycopy(rawIv, 0, ivBytes, 0, minOf(16, rawIv.size))

            var cleanEnc = enc.replace('-', '+').replace('_', '/')
            while (cleanEnc.length % 4 != 0) {
                cleanEnc += "="
            }
            val cipherBytes = Base64.decode(cleanEnc, Base64.DEFAULT)

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)

            // Try PKCS5Padding first (standard for Megaplay)
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
                val decrypted = cipher.doFinal(cipherBytes)
                val text = String(decrypted, Charsets.UTF_8).trim()
                if (text.isNotBlank()) return text
            } catch (_: Exception) {}

            // Fallback to NoPadding
            val cipherNoPad = Cipher.getInstance("AES/CBC/NoPadding")
            cipherNoPad.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedNoPad = cipherNoPad.doFinal(cipherBytes)
            val rawText = String(decryptedNoPad, Charsets.UTF_8).trim()
            rawText
        } catch (e: Exception) {
            Log.e(TAG, "decryptMegaplayEnc failed: ${e.message}")
            null
        }
    }

    /**
     * Dynamically updates AES keys if Megaplay updates its client scripts.
     */
    private fun refreshMegaplayKeysFromScript(embedOrigin: String) {
        try {
            val scripts = listOf(
                "$embedOrigin/lib/newclient.min.js?v=4.17",
                "$embedOrigin/lib/newclient.min.js",
                "$embedOrigin/lib/e1-player.min.js"
            )
            for (scriptUrl in scripts) {
                val req = Request.Builder()
                    .url(scriptUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "$embedOrigin/")
                    .build()
                val res = httpClient.newCall(req).execute()
                val body = res.body?.string() ?: ""
                if (body.contains("trustAesKey") || body.contains("TRUST_AES_KEY")) {
                    val keyMatch = Regex("""\[["'`]trustAesKey["'`]\s*,\s*["'`]TRUST_AES_KEY["'`]\]\s*,\s*["'`]?([^"'`]+)["'`]?""").find(body)
                    val ivMatch = Regex("""\[["'`]trustAesIv["'`]\s*,\s*["'`]TRUST_AES_IV["'`]\]\s*,\s*["'`]?([^"'`]+)["'`]?""").find(body)
                    val newKey = keyMatch?.groupValues?.get(1)?.trim()
                    val newIv = ivMatch?.groupValues?.get(1)?.trim()
                    if (!newKey.isNullOrBlank() && newKey.length >= 8) {
                        cachedMegaplayAesKey = newKey
                    }
                    if (!newIv.isNullOrBlank() && newIv.length >= 8) {
                        cachedMegaplayAesIv = newIv
                    }
                    Log.d(TAG, "Refreshed Megaplay AES keys from script: keyLen=${cachedMegaplayAesKey.length}, ivLen=${cachedMegaplayAesIv.length}")
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed refreshing Megaplay keys: ${e.message}")
        }
    }

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
     * Enhanced Season Extraction API
     */
    suspend fun fetchSeasons(watchUrlOrSlug: String, animeTitle: String? = null): List<AnikotoSeason> = withContext(Dispatchers.IO) {
        val seasons = mutableListOf<AnikotoSeason>()
        try {
            var fullUrl = watchUrlOrSlug
            if (!fullUrl.startsWith("http")) {
                fullUrl = "$BASE_URL/watch/$watchUrlOrSlug"
            }
            val html = fetchHtml(fullUrl, referer = "$BASE_URL/")
            val doc = if (html.isNotBlank()) Jsoup.parse(html, fullUrl) else null

            val pageTitle = doc?.selectFirst(".name.d-title, .film-name, h2.title, h1, .film-name a")?.text()?.trim()
                ?: animeTitle ?: ""

            // Helper to parse season number from title text
            fun extractSeasonNumber(text: String, fallback: Int = 1): Int {
                val sMatch = Regex("""(?i)\b(?:Season|S|Part|Cour)\s*(\d+)\b""").find(text)
                if (sMatch != null) return sMatch.groupValues[1].toIntOrNull() ?: fallback
                val ordMatch = Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s*Season\b""").find(text)
                if (ordMatch != null) return ordMatch.groupValues[1].toIntOrNull() ?: fallback
                val romanMatch = Regex("""(?i)\b(?:Season|S)\s*(I|II|III|IV|V|VI|VII|VIII)\b""").find(text)
                if (romanMatch != null) {
                    return when (romanMatch.groupValues[1].uppercase()) {
                        "I" -> 1; "II" -> 2; "III" -> 3; "IV" -> 4; "V" -> 5; "VI" -> 6; "VII" -> 7; "VIII" -> 8; else -> fallback
                    }
                }
                if (Regex("""(?i)\bFinal\s*Season\b""").containsMatchIn(text)) return 4
                return fallback
            }

            val currentNum = extractSeasonNumber(pageTitle, 1)

            // Add current page as a known season
            seasons.add(
                AnikotoSeason(
                    number = currentNum,
                    title = if (pageTitle.isNotBlank()) pageTitle else "Season $currentNum",
                    watchUrl = fullUrl
                )
            )

            // 1. Direct on-page season elements
            if (doc != null) {
                val seasonElements = doc.select(".os-list .os-item, .os-list a, .seasons-block a, .seasons-block .item, #seasons a, .dropdown-menu a[href*='/watch/'], #ani-seasons a, #ani-seasons .item, #w-related a[href*='/watch/'], .block_area-seasons a, .ss-list a[href*='/watch/']")
                var counter = 1
                for (el in seasonElements) {
                    val href = el.attr("href") ?: ""
                    if (href.contains("/watch/") && !href.contains("/episode/")) {
                        val title = el.text().trim().ifEmpty { el.attr("title").trim() }.ifEmpty { "Season $counter" }
                        val fullSeasonUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                        val num = extractSeasonNumber(title, counter)
                        if (seasons.none { it.watchUrl == fullSeasonUrl }) {
                            seasons.add(
                                AnikotoSeason(
                                    number = num,
                                    title = title,
                                    watchUrl = fullSeasonUrl
                                )
                            )
                            counter++
                        }
                    }
                }

                // 2. Fetch watch-order / relations via Ajax if animeDataId is present
                var animeDataId = doc.selectFirst(".anis-watch-wrap[data-id], #wrapper[data-id], .film_detail[data-id], #detail-infor[data-id], [data-id]")?.attr("data-id") ?: ""
                if (animeDataId.isBlank()) {
                    val match = Regex("""(?:data-id|anime_id|animeId|movie_id|film_id|syncId)\s*[:=]\s*["']?([a-zA-Z0-9_-]+)["']?""").find(html)
                    if (match != null) {
                        animeDataId = match.groupValues.getOrNull(1) ?: ""
                    }
                }
                if (animeDataId.isBlank()) {
                    val cleanPath = fullUrl.substringBefore("/ep-").substringBefore("?").trimEnd('/')
                    val lastDash = cleanPath.substringAfterLast("-")
                    if (lastDash.isNotBlank() && lastDash.length in 2..8) {
                        animeDataId = lastDash
                    }
                }

                if (animeDataId.isNotBlank()) {
                    val woUrls = listOf(
                        "$BASE_URL/api/watch-order/$animeDataId",
                        "$AJAX_URL/watch-order/$animeDataId",
                        "$AJAX_URL/season/list/$animeDataId"
                    )
                    for (woUrl in woUrls) {
                        try {
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
                                val items = woDoc.select(".item a[href*='/watch/'], a[href*='/watch/']")
                                for (el in items) {
                                    val href = el.attr("href") ?: ""
                                    if (href.contains("/watch/")) {
                                        val fullSeasonUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                                        val nameEl = el.selectFirst(".name, .d-title, .title") ?: el
                                        val itemTitle = nameEl.text().trim().ifEmpty { el.attr("title").trim() }
                                        if (itemTitle.isNotEmpty() && seasons.none { it.watchUrl == fullSeasonUrl }) {
                                            val num = extractSeasonNumber(itemTitle, seasons.size + 1)
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
                }
            }

            // 3. Franchise search for all seasons
            val baseTitleCandidate = if (pageTitle.isNotBlank()) pageTitle else (animeTitle ?: "")
            val baseTitle = baseTitleCandidate
                .replace(Regex("""(?i)\s*(?:[-–—:]\s*)?(?:Season\s*\d+|[0-9]+(?:st|nd|rd|th)\s*Season|Part\s*\d+|Cour\s*\d+|Final\s*Season|Final\s*Chapter|II|III|IV|V|VI).*$"""), "")
                .replace(Regex("""(?i)\s*\((?:TV|Dub|Sub|Official)\)"""), "")
                .trim()

            if (baseTitle.length >= 3) {
                try {
                    val searchItems = searchOrFilterAnime(keyword = baseTitle, sortBy = "latest-updated")
                    val normalizedBase = baseTitle.lowercase().replace(Regex("[^a-z0-9]"), "")

                    for (sItem in searchItems) {
                        val itemNorm = sItem.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                        val isRelatedFranchise = itemNorm.contains(normalizedBase) || normalizedBase.contains(itemNorm)
                        val isExcluded = sItem.type.equals("Movie", ignoreCase = true) && !sItem.title.contains("Mugen Train", ignoreCase = true)

                        if (isRelatedFranchise && !isExcluded) {
                            val num = extractSeasonNumber(sItem.title, if (itemNorm == normalizedBase) 1 else seasons.size + 1)
                            if (seasons.none { it.watchUrl == sItem.watchUrl }) {
                                seasons.add(
                                    AnikotoSeason(
                                        number = num,
                                        title = sItem.title,
                                        watchUrl = sItem.watchUrl
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Franchise season search failed: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "fetchSeasons error: ${e.message}", e)
        }

        if (seasons.isEmpty()) {
            seasons.add(AnikotoSeason(number = 1, title = "Season 1", watchUrl = watchUrlOrSlug))
        }

        val distinctSeasons = seasons.distinctBy { it.watchUrl }
        val sorted = distinctSeasons.sortedWith(
            compareBy<AnikotoSeason> { it.number }
                .thenBy { it.title }
        )

        val finalSeasons = mutableListOf<AnikotoSeason>()
        var nextNum = 1
        for (s in sorted) {
            val assignedNum = if (finalSeasons.none { it.number == s.number }) s.number else nextNum
            finalSeasons.add(s.copy(number = assignedNum))
            nextNum = maxOf(nextNum, assignedNum) + 1
        }

        finalSeasons.sortedBy { it.number }
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

            val seasons = fetchSeasons(fullUrl, title)
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
     * - Checks internal anime ID from DOM, script tags, and URL slug
     * - Hits GET https://anikoto.cz/ajax/episode/list/{anime_internal_id}
     * - Hits fallback endpoints for full coverage
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
            val doc = if (html.isNotBlank()) Jsoup.parse(html, fullUrl) else null

            // 1. Get internal anime ID from DOM, script tags, or URL slug
            val animeDataIds = mutableListOf<String>()

            if (doc != null) {
                val dataIdAttr = doc.selectFirst(".anis-watch-wrap[data-id], #wrapper[data-id], .film_detail[data-id], #detail-infor[data-id], .watch-player[data-id], [data-id]")?.attr("data-id") ?: ""
                if (dataIdAttr.isNotBlank() && dataIdAttr.length <= 15) {
                    animeDataIds.add(dataIdAttr)
                }

                val inputId = doc.selectFirst("input#movie_id, input#anime_id, input[name='movie_id'], input[name='anime_id']")?.attr("value") ?: ""
                if (inputId.isNotBlank() && !animeDataIds.contains(inputId)) {
                    animeDataIds.add(inputId)
                }

                val scriptText = doc.html()
                val match = Regex("""(?:data-id|anime_id|animeId|movie_id|film_id|syncId)\s*[:=]\s*["']?([a-zA-Z0-9_-]+)["']?""").find(scriptText)
                if (match != null) {
                    val idFromScript = match.groupValues.getOrNull(1) ?: ""
                    if (idFromScript.isNotBlank() && !animeDataIds.contains(idFromScript)) {
                        animeDataIds.add(idFromScript)
                    }
                }
            }

            // Slug id e.g. solo-leveling-lh0li/ep-1 -> lh0li, or naruto-674 -> 674
            val cleanPath = fullUrl.substringBefore("/ep-").substringBefore("?").trimEnd('/')
            val lastDash = cleanPath.substringAfterLast("-").substringAfterLast("/")
            if (lastDash.isNotBlank() && lastDash.length in 2..12 && !animeDataIds.contains(lastDash)) {
                animeDataIds.add(lastDash)
            }

            val ajaxUrls = mutableListOf<String>()
            for (id in animeDataIds) {
                ajaxUrls.add("$AJAX_URL/episode/list/$id")
                ajaxUrls.add("$BASE_URL/ajax/v2/episode/list/$id")
                ajaxUrls.add("$BASE_URL/ajax/episode/list/$id")
                ajaxUrls.add("$AJAX_URL/season/episodes/$id")
                ajaxUrls.add("$AJAX_URL/panel?id=$id")
            }

            for (ajaxUrl in ajaxUrls) {
                try {
                    val request = Request.Builder()
                        .url(ajaxUrl)
                        .header("User-Agent", DEFAULT_UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", fullUrl)
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .build()

                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string() ?: ""
                    var resultHtml = body
                    if (body.startsWith("{")) {
                        val jsObj = JSONObject(body)
                        resultHtml = jsObj.optString("result", jsObj.optString("html", jsObj.optString("data", body)))
                    }

                    if (resultHtml.isNotBlank()) {
                        val epDoc = Jsoup.parse(resultHtml)
                        val epElements = epDoc.select(".episodes.name a, .ep-range a, a[data-id], a[data-ids], a.ep-item, li[title] a, .episodes a, .ssl-item, .ep-item, a[href*='?ep='], a[href*='/ep-']")
                        for (epEl in epElements) {
                            val dataId = epEl.attr("data-id").ifEmpty { epEl.attr("data-ids") }.ifEmpty { epEl.attr("data-number") }.ifEmpty { epEl.attr("id") }
                            val dataIds = epEl.attr("data-ids").ifEmpty { epEl.attr("data-id") }.ifEmpty { dataId }

                            val num = epEl.attr("data-number").toIntOrNull()
                                ?: epEl.attr("data-num").toIntOrNull()
                                ?: epEl.attr("data-slug").toIntOrNull()
                                ?: epEl.selectFirst(".ssli-order, .order, .ep-order, .num, .number, .ep-no")?.text()?.trim()?.toIntOrNull()
                                ?: Regex("""[?&]ep=(\d+)""").find(epEl.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                                ?: Regex("""/ep-(\d+)""").find(epEl.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                                ?: Regex("""(?i)\b(?:Episode|Ep\.?|#)\s*(\d+)\b""").find(epEl.attr("title").ifEmpty { epEl.text() })?.groupValues?.get(1)?.toIntOrNull()
                                ?: (episodes.size + 1)

                            val rawTitle = epEl.attr("title").ifEmpty { epEl.parent()?.attr("title") ?: "" }
                            val innerName = epEl.selectFirst(".ep-name, .ssli-detail .name, .d-title, .ep-title, .name")?.text()?.trim() ?: ""
                            val title = when {
                                innerName.isNotBlank() && !innerName.equals("Episode $num", ignoreCase = true) -> "Episode $num: $innerName"
                                rawTitle.isNotBlank() -> rawTitle
                                else -> epEl.text().trim().ifEmpty { "Episode $num" }
                            }

                            if (dataId.isNotBlank() && episodes.none { it.number == num }) {
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
            if (episodes.isEmpty() && doc != null) {
                val onPageEps = doc.select(".episodes.name a, .ep-range a, a[data-id], a[data-ids], .ss-list a, .ep-item, a[href*='/ep-'], a[href*='?ep='], #episodes a, .film-episodes a")
                for (epEl in onPageEps) {
                    val href = epEl.attr("href")
                    if (href.contains("/ep-") || href.contains("?ep=") || epEl.hasAttr("data-id") || epEl.hasAttr("data-number")) {
                        val dataId = epEl.attr("data-id").ifEmpty { epEl.attr("data-ids") }.ifEmpty { epEl.attr("data-number") }
                        val dataIds = epEl.attr("data-ids").ifEmpty { dataId }
                        val num = epEl.attr("data-number").toIntOrNull()
                            ?: epEl.attr("data-num").toIntOrNull()
                            ?: Regex("""/ep-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""[?&]ep=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            ?: (episodes.size + 1)
                        val rawTitle = epEl.attr("title").ifEmpty { epEl.text().trim() }
                        val title = if (rawTitle.isNotBlank()) rawTitle else "Episode $num"
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
                        var embedUrl = resultObj?.optString("link", resultObj.optString("url", "")) 
                            ?: json.optString("link", json.optString("url", ""))
                        if (embedUrl.isNotBlank()) {
                            if (embedUrl.startsWith("//")) {
                                embedUrl = "https:$embedUrl"
                            } else if (embedUrl.startsWith("/")) {
                                embedUrl = "$BASE_URL$embedUrl"
                            }
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

            // 1. Gather all candidate IDs from HTML attributes, data-realid, and URL path
            val doc = Jsoup.parse(html)
            val playerEl = doc.selectFirst("#megaplay-player, [data-id], .fix-area")
            val realIdAttr = playerEl?.attr("data-realid")?.takeIf { it.isNotBlank() }
            val dataIdAttr = playerEl?.attr("data-id")?.takeIf { it.isNotBlank() }
            val pathId = Regex("""/(\d+)(?:/|$)""").find(embedUrl)?.groupValues?.get(1)
            val lastSeg = embedUri.lastPathSegment?.substringBefore("?") ?: ""
            val lastSegId = if (lastSeg.all { it.isDigit() } && lastSeg.isNotBlank()) lastSeg else null

            val candidateIds = listOfNotNull(realIdAttr, pathId, lastSegId, dataIdAttr).distinct()
            Log.d(TAG, "Embed candidate IDs: $candidateIds from $embedUrl")

            // 2. Query stream/getSources endpoint
            for (candidateId in candidateIds) {
                if (m3u8Url != null) break
                val sourceEndpoints = listOf(
                    "$embedOrigin/stream/getSources?id=$candidateId",
                    "$embedOrigin/stream/getSourcesNew?id=$candidateId",
                    "$embedOrigin/embed-2/ajax/e-1/getSources?id=$candidateId",
                    "$embedOrigin/ajax/getSources?id=$candidateId"
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

                            // Check encrypted payload ("enc") if unencrypted sources not found
                            if (m3u8Url.isNullOrBlank()) {
                                val enc = json.optString("enc", "")
                                if (enc.isNotBlank()) {
                                    var decText = decryptMegaplayEnc(enc)
                                    if (decText.isNullOrBlank() || !decText.contains("m3u8")) {
                                        refreshMegaplayKeysFromScript(embedOrigin)
                                        decText = decryptMegaplayEnc(enc)
                                    }
                                    if (!decText.isNullOrBlank()) {
                                        Log.d(TAG, "Decrypted Megaplay payload successfully")
                                        if (decText.startsWith("{")) {
                                            try {
                                                val decJson = JSONObject(decText)
                                                val f = decJson.optString("file", "")
                                                if (f.isNotBlank()) {
                                                    m3u8Url = f
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        if (m3u8Url.isNullOrBlank()) {
                                            val m = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(decText)
                                            if (m != null) {
                                                m3u8Url = m.value
                                            }
                                        }
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
                                        if (subtitles.none { it.url == file }) {
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

            // 3. Fallback: inspect nested iframe if needed
            if (m3u8Url.isNullOrBlank()) {
                val iframeSrc = doc.selectFirst("iframe[src]")?.attr("src")
                if (!iframeSrc.isNullOrBlank() && !iframeSrc.startsWith("javascript") && iframeSrc != embedUrl) {
                    val fullIframeUrl = if (iframeSrc.startsWith("http")) iframeSrc else "$embedOrigin$iframeSrc"
                    val subRes = extractM3u8AndSubtitlesFromEmbed(fullIframeUrl)
                    if (subRes != null && subRes.streamUrl.isNotBlank()) {
                        return@withContext subRes
                    }
                }
            }

            // 4. Fallback Regex Parsing if not resolved
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

            val seasons = fetchSeasons(matchedWatchUrl, title)
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
        watchUrl: String = BASE_URL,
        episode: Int = 1
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
                        referer = streamResult.referer,
                        subtitles = streamResult.subtitles.map { SubtitleTrack(it.url, it.lang, it.label, it.default) }
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
            val seasons = fetchSeasons(matchedWatchUrl, title)
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

            // Filter preferDub if requested, otherwise prioritize sub
            val preferredServers = if (preferDub) {
                servers.filter { it.type.equals("dub", ignoreCase = true) } + servers.filter { !it.type.equals("dub", ignoreCase = true) }
            } else {
                servers.filter { it.type.equals("sub", ignoreCase = true) } + servers.filter { !it.type.equals("sub", ignoreCase = true) }
            }

            // Step 5: Pick server and extract embed URL
            for (srv in preferredServers) {
                try {
                    val embedUrl = extractServerEmbedUrl(srv.linkId, seasonWatchUrl)
                    if (!embedUrl.isNullOrBlank()) {
                        Log.d(TAG, "Extracted Embed URL: $embedUrl from ${srv.name}")
                        val streamResult = extractM3u8AndSubtitlesFromEmbed(embedUrl)
                        if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                            return@withContext ScrapedStreamResult(
                                streamUrl = streamResult.streamUrl,
                                headers = streamResult.headers,
                                referer = streamResult.referer,
                                subtitles = streamResult.subtitles.map { SubtitleTrack(it.url, it.lang, it.label, it.default) }
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
