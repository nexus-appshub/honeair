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
import org.json.JSONArray
import org.json.JSONObject
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
    val referer: String = ""
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
    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(DiagnosticLoggingInterceptor(tag = "AnimeApi-Diagnostic", enforceBypassHeaders = true))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun fetchJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "fetchJson error for $url: ${e.message}")
            null
        }
    }

    private fun makeAbsoluteUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.startsWith("/")) {
            "$API_BASE_URL$trimmed"
        } else {
            "$API_BASE_URL/$trimmed"
        }
    }

    /**
     * 1. Search & Discovery: Filter / Search / Browse Anime from https://media.hmair.xyz/api/anikoto/search
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
                    val poster = itemObj.optString("poster", "")
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
            Log.e(TAG, "searchOrFilterAnime error: ${e.message}", e)
        }
        results
    }

    suspend fun getLiveSuggestions(keyword: String): List<AnikotoAnimeItem> = withContext(Dispatchers.IO) {
        searchOrFilterAnime(keyword = keyword)
    }

    /**
     * 2. Fetch Seasons for an anime from API
     */
    suspend fun fetchSeasons(watchUrlOrSlug: String, animeTitle: String? = null): List<AnikotoSeason> = withContext(Dispatchers.IO) {
        val seasons = mutableListOf<AnikotoSeason>()
        try {
            val queryParams = mutableListOf<String>()
            if (watchUrlOrSlug.startsWith("http") || watchUrlOrSlug.contains("/watch/")) {
                queryParams.add("url=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            } else if (!animeTitle.isNullOrBlank()) {
                queryParams.add("keyword=${URLEncoder.encode(animeTitle.trim(), "UTF-8")}")
            } else {
                queryParams.add("keyword=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            }
            queryParams.add("ep=1")

            val url = "$API_BASE_URL/api/stream/get?${queryParams.joinToString("&")}"
            Log.d(TAG, "Fetching Seasons from API: $url")
            val json = fetchJson(url)

            if (json != null) {
                val seasonsArray = json.optJSONArray("seasons")
                if (seasonsArray != null && seasonsArray.length() > 0) {
                    for (i in 0 until seasonsArray.length()) {
                        val sObj = seasonsArray.optJSONObject(i) ?: continue
                        val title = sObj.optString("title", "Season ${i + 1}")
                        val link = sObj.optString("link", "")
                        val poster = sObj.optString("poster", "")
                        val subCount = sObj.optString("subCount", "")
                        val dubCount = sObj.optString("dubCount", "")

                        val seasonNumber = extractSeasonNumber(title, i + 1)
                        seasons.add(
                            AnikotoSeason(
                                number = seasonNumber,
                                title = title,
                                watchUrl = link.ifBlank { watchUrlOrSlug },
                                posterUrl = poster,
                                subCount = subCount,
                                dubCount = dubCount
                            )
                        )
                    }
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

    private fun extractSeasonNumber(title: String, defaultNum: Int): Int {
        val regex = Regex("""(?i)(?:season|part|s)\s*(\d+)""")
        val match = regex.find(title)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: defaultNum
    }

    /**
     * 3. Fetch Anime Details from API
     */
    suspend fun fetchAnimeDetails(watchUrlOrSlug: String): AnikotoDetails? = withContext(Dispatchers.IO) {
        try {
            val queryParams = mutableListOf<String>()
            if (watchUrlOrSlug.startsWith("http") || watchUrlOrSlug.contains("/watch/")) {
                queryParams.add("url=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            } else {
                queryParams.add("keyword=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
            }
            queryParams.add("ep=1")

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

            return@withContext AnikotoDetails(
                title = title,
                posterUrl = poster,
                description = "",
                rating = "8.5",
                releaseYear = "2024",
                totalSeasons = seasonCount,
                totalEpisodes = epCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchAnimeDetails error: ${e.message}", e)
            null
        }
    }

    /**
     * 4. Fetch Episode List for a given anime / season from API
     */
    suspend fun fetchEpisodes(watchUrlOrSlug: String): List<AnikotoEpisode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<AnikotoEpisode>()
        try {
            // First check direct episodes endpoint if slug/id is provided
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

            // If empty, query stream get endpoint
            if (episodes.isEmpty()) {
                val queryParams = mutableListOf<String>()
                if (watchUrlOrSlug.startsWith("http") || watchUrlOrSlug.contains("/watch/")) {
                    queryParams.add("url=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
                } else {
                    queryParams.add("keyword=${URLEncoder.encode(watchUrlOrSlug.trim(), "UTF-8")}")
                }
                queryParams.add("ep=1")

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
            for (i in 1..12) {
                episodes.add(AnikotoEpisode(id = "$i", number = i, title = "Episode $i", dataIdsToken = "$i"))
            }
        }

        episodes.distinctBy { it.number }.sortedBy { it.number }
    }

    /**
     * 5. Fetch available SUB & DUB streaming servers from API
     */
    suspend fun fetchAvailableServers(
        title: String,
        season: Int = 1,
        episode: Int = 1
    ): AnikotoServerGroup = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = sanitizeSearchTitle(title)
            Log.d(TAG, "Fetching available SUB / DUB servers via API for: $cleanTitle (Ep $episode)")

            val subServers = mutableListOf<AnikotoServer>()
            val dubServers = mutableListOf<AnikotoServer>()

            // Query SUB servers
            val subUrl = buildStreamGetUrl(title = cleanTitle, episode = episode, type = "sub")
            val subJson = fetchJson(subUrl)

            if (subJson != null) {
                val availableServers = subJson.optJSONArray("availableServers")
                if (availableServers != null) {
                    for (i in 0 until availableServers.length()) {
                        val srvObj = availableServers.optJSONObject(i) ?: continue
                        val srvName = srvObj.optString("name", "Server ${i + 1}")
                        val srvId = srvObj.optString("server", "HD-1")
                        val audioType = srvObj.optString("audioType", "SUB").uppercase()
                        val streamUrl = makeAbsoluteUrl(srvObj.optString("streamUrl", ""))
                        val rawUrl = srvObj.optString("rawUrl", "")
                        val referer = srvObj.optString("referer", "$API_BASE_URL/")

                        val parsedServer = AnikotoServer(
                            id = srvId,
                            linkId = streamUrl,
                            name = if (srvName.contains("(")) srvName.substringAfter("(").substringBefore(")") else srvName,
                            type = audioType.lowercase(),
                            streamUrl = streamUrl,
                            rawUrl = rawUrl,
                            referer = referer
                        )

                        if (audioType == "DUB") {
                            dubServers.add(parsedServer)
                        } else {
                            subServers.add(parsedServer)
                        }
                    }
                }
            }

            // Query DUB servers if none found in first response
            if (dubServers.isEmpty()) {
                val dubUrl = buildStreamGetUrl(title = cleanTitle, episode = episode, type = "dub")
                val dubJson = fetchJson(dubUrl)
                if (dubJson != null) {
                    val availableServers = dubJson.optJSONArray("availableServers")
                    if (availableServers != null) {
                        for (i in 0 until availableServers.length()) {
                            val srvObj = availableServers.optJSONObject(i) ?: continue
                            val srvName = srvObj.optString("name", "Server ${i + 1}")
                            val srvId = srvObj.optString("server", "HD-1")
                            val audioType = srvObj.optString("audioType", "DUB").uppercase()
                            val streamUrl = makeAbsoluteUrl(srvObj.optString("streamUrl", ""))
                            val rawUrl = srvObj.optString("rawUrl", "")
                            val referer = srvObj.optString("referer", "$API_BASE_URL/")

                            if (audioType == "DUB") {
                                dubServers.add(
                                    AnikotoServer(
                                        id = srvId,
                                        linkId = streamUrl,
                                        name = if (srvName.contains("(")) srvName.substringAfter("(").substringBefore(")") else srvName,
                                        type = "dub",
                                        streamUrl = streamUrl,
                                        rawUrl = rawUrl,
                                        referer = referer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Distinct and name servers nicely
            val cleanSub = subServers.distinctBy { it.name + it.id }.mapIndexed { idx, srv ->
                val displayName = if (srv.name.isNotBlank()) "Server ${idx + 1} (${srv.name})" else "Server ${idx + 1}"
                srv.copy(name = displayName)
            }
            val cleanDub = dubServers.distinctBy { it.name + it.id }.mapIndexed { idx, srv ->
                val displayName = if (srv.name.isNotBlank()) "Server ${idx + 1} (${srv.name})" else "Server ${idx + 1}"
                srv.copy(name = displayName)
            }

            Log.d(TAG, "API returned ${cleanSub.size} SUB servers and ${cleanDub.size} DUB servers for $cleanTitle")
            AnikotoServerGroup(
                subServers = cleanSub,
                dubServers = cleanDub,
                watchUrl = title,
                episodeNum = episode
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchAvailableServers error: ${e.message}", e)
            AnikotoServerGroup(emptyList(), emptyList(), "", episode)
        }
    }

    /**
     * 6. Extract Direct Playable Stream from a specific selected server
     */
    suspend fun extractStreamFromServer(
        server: AnikotoServer,
        watchUrl: String = "",
        episode: Int = 1
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            if (server.streamUrl.isNotBlank()) {
                val finalUrl = makeAbsoluteUrl(server.streamUrl)
                val headers = mutableMapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to if (server.referer.isNotBlank()) server.referer else "$API_BASE_URL/"
                )
                Log.d(TAG, "extractStreamFromServer returning cached/direct server URL: $finalUrl")
                return@withContext ScrapedStreamResult(
                    streamUrl = finalUrl,
                    headers = headers,
                    referer = headers["Referer"] ?: "$API_BASE_URL/",
                    subtitles = emptyList()
                )
            }

            // If streamUrl not pre-filled, query the stream API with server name
            val isDub = server.type.lowercase() == "dub"
            val targetType = if (isDub) "dub" else "sub"
            val effectiveEp = if (episode <= 0) 1 else episode
            val queryUrl = "$API_BASE_URL/api/stream/get?keyword=${URLEncoder.encode(watchUrl, "UTF-8")}&ep=$effectiveEp&type=$targetType&server=${URLEncoder.encode(server.id, "UTF-8")}"
            val json = fetchJson(queryUrl)
            val selected = json?.optJSONObject("selectedStream")
            if (selected != null) {
                val sUrl = makeAbsoluteUrl(selected.optString("streamUrl", ""))
                if (sUrl.isNotBlank()) {
                    val referer = selected.optString("referer", "$API_BASE_URL/")
                    val subtitles = parseSubtitles(selected.optJSONArray("tracks"))
                    return@withContext ScrapedStreamResult(
                        streamUrl = sUrl,
                        headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to referer),
                        referer = referer,
                        subtitles = subtitles
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractStreamFromServer error: ${e.message}", e)
        }
        null
    }

    /**
     * 7. Primary Endpoint: Get Instant Stream M3U8 & Subtitles from API
     */
    suspend fun getStreamByTitle(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        preferDub: Boolean = false
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val effectiveEp = if (episode <= 0) 1 else episode
            val audioType = if (preferDub) "dub" else "sub"
            val cleanTitle = sanitizeSearchTitle(title)
            Log.d(TAG, "Initiating Anime API stream fetch for: $cleanTitle (S$season Ep $effectiveEp, audio: $audioType)")

            // Call primary API stream endpoint
            val apiUrl = buildStreamGetUrl(title = cleanTitle, episode = effectiveEp, type = audioType)
            var json = fetchJson(apiUrl)

            // Fallback 1: If exact query returned no stream, try fallback with clean base title
            if (json == null || json.optBoolean("success") != true || json.optJSONObject("selectedStream") == null) {
                val fallbackWords = cleanTitle.split(" ").filter { it.isNotBlank() }
                if (fallbackWords.size > 2) {
                    val fallbackTitle = fallbackWords.take(2).joinToString(" ")
                    Log.d(TAG, "Attempting Fallback Anime API stream fetch: $fallbackTitle")
                    val fallbackUrl = buildStreamGetUrl(title = fallbackTitle, episode = effectiveEp, type = audioType)
                    json = fetchJson(fallbackUrl)
                }
            }

            // Fallback 2: Try raw uncleaned title if cleanTitle failed
            if (json == null || json.optBoolean("success") != true || json.optJSONObject("selectedStream") == null) {
                if (title != cleanTitle) {
                    val rawUrl = buildStreamGetUrl(title = title, episode = effectiveEp, type = audioType)
                    json = fetchJson(rawUrl)
                }
            }

            // Fallback 3: Perform Anime Search to find exact watchUrl / slug
            if (json == null || json.optBoolean("success") != true || json.optJSONObject("selectedStream") == null) {
                try {
                    val searchResults = searchOrFilterAnime(keyword = cleanTitle)
                    val matchedItem = searchResults.firstOrNull()
                    if (matchedItem != null && matchedItem.watchUrl.isNotBlank()) {
                        Log.d(TAG, "Search fallback matched anime watchUrl: ${matchedItem.watchUrl}")
                        val searchUrl = buildStreamGetUrl(title = matchedItem.watchUrl, episode = effectiveEp, type = audioType)
                        json = fetchJson(searchUrl)
                        if (json == null || json.optBoolean("success") != true) {
                            val searchUrlEp1 = buildStreamGetUrl(title = matchedItem.watchUrl, episode = 1, type = audioType)
                            json = fetchJson(searchUrlEp1)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Search fallback error in getStreamByTitle: ${e.message}")
                }
            }

            // Fallback 4: For Episode 1, if preferred audio type returned null, try alternate audio type (sub/dub)
            if ((json == null || json.optBoolean("success") != true || json.optJSONObject("selectedStream") == null) && effectiveEp == 1) {
                val altType = if (audioType == "sub") "dub" else "sub"
                val altUrl = buildStreamGetUrl(title = cleanTitle, episode = 1, type = altType)
                json = fetchJson(altUrl)
            }

            if (json != null && json.optBoolean("success") == true) {
                val selectedStream = json.optJSONObject("selectedStream")
                if (selectedStream != null) {
                    val rawStreamUrl = selectedStream.optString("streamUrl", "")
                    if (rawStreamUrl.isNotBlank()) {
                        val finalStreamUrl = makeAbsoluteUrl(rawStreamUrl)
                        val referer = selectedStream.optString("referer", "$API_BASE_URL/")
                        val subtitles = parseSubtitles(selectedStream.optJSONArray("tracks"))

                        Log.d(TAG, "Anime Stream API successfully resolved: $finalStreamUrl with ${subtitles.size} subtitles")
                        return@withContext ScrapedStreamResult(
                            streamUrl = finalStreamUrl,
                            headers = mapOf(
                                "User-Agent" to DEFAULT_UA,
                                "Referer" to referer
                            ),
                            referer = referer,
                            subtitles = subtitles
                        )
                    }
                }
            }

            // Fallback 4: Extract from available servers directly if API endpoint returned no stream
            try {
                val serverGroup = fetchAvailableServers(title = cleanTitle, season = season, episode = effectiveEp)
                val firstServer = serverGroup.subServers.firstOrNull() ?: serverGroup.dubServers.firstOrNull()
                if (firstServer != null) {
                    val serverStream = extractStreamFromServer(firstServer)
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

    private fun buildStreamGetUrl(title: String, episode: Int, type: String, server: String? = null): String {
        val queryParams = mutableListOf<String>()
        if (title.startsWith("http") || title.contains("/watch/")) {
            queryParams.add("url=${URLEncoder.encode(title.trim(), "UTF-8")}")
        } else {
            queryParams.add("keyword=${URLEncoder.encode(title.trim(), "UTF-8")}")
        }
        queryParams.add("ep=$episode")
        queryParams.add("type=$type")
        if (!server.isNullOrBlank()) {
            queryParams.add("server=${URLEncoder.encode(server.trim(), "UTF-8")}")
        }
        return "$API_BASE_URL/api/stream/get?${queryParams.joinToString("&")}"
    }

    private fun sanitizeSearchTitle(title: String): String {
        return title
            .replace(Regex("""\b(?:Season|S)\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bPart\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""[:\-–—]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
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
                    label.contains("Fre", ignoreCase = true) -> "fr"
                    label.contains("Ger", ignoreCase = true) -> "de"
                    label.contains("Ita", ignoreCase = true) -> "it"
                    label.contains("Por", ignoreCase = true) -> "pt"
                    label.contains("Rus", ignoreCase = true) -> "ru"
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

    // Helper functions for backwards compatibility with downloader and tools
    fun extractServerEmbedUrl(linkId: String, referer: String): String? {
        return if (linkId.isNotBlank()) makeAbsoluteUrl(linkId) else null
    }

    suspend fun extractM3u8AndSubtitlesFromEmbed(embedUrl: String): AnikotoStreamResult? = withContext(Dispatchers.IO) {
        val absUrl = makeAbsoluteUrl(embedUrl)
        AnikotoStreamResult(
            streamUrl = absUrl,
            headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to "$API_BASE_URL/"),
            referer = "$API_BASE_URL/",
            subtitles = emptyList(),
            serverName = "Server",
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
