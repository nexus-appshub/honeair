package com.example.scraper

import android.content.Context
import android.util.Log
import com.example.data.api.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object UnifiedStreamManager {
    private const val TAG = "UnifiedStreamManager"
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, ScrapedStreamResult>()

    fun getCachedStream(tmdbId: String, season: Int = 1, episode: Int = 1): ScrapedStreamResult? {
        val key = "$tmdbId-$season-$episode"
        return streamCache[key]
    }

    suspend fun getStream(
        context: Context,
        title: String,
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        isAnime: Boolean = false
    ): ScrapedStreamResult? {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val cacheKey = "$tmdbId-$season-$effectiveEpisode"
        streamCache[cacheKey]?.let {
            if (it.streamUrl.isNotBlank()) return it
        }

        // Try to load from Local Room Cache first to prevent redundant scraping
        try {
            val db = com.example.data.database.AppDatabase.getDatabase(context)
            val cachedEntity = db.scrapedStreamDao().getStreamById(cacheKey)
            if (cachedEntity != null) {
                // Check if it is fresh (within 24 hours)
                if (System.currentTimeMillis() - cachedEntity.timestamp < 24 * 60 * 60 * 1000L) {
                    val headers = jsonToMap(cachedEntity.headersJson)
                    val result = ScrapedStreamResult(
                        streamUrl = cachedEntity.streamUrl,
                        headers = headers,
                        referer = cachedEntity.referer
                    )
                    streamCache[cacheKey] = result
                    Log.d(TAG, "Loaded stream from local Room cache for key: $cacheKey")
                    return result
                } else {
                    Log.d(TAG, "Cached stream in Room expired for key: $cacheKey. Deleting...")
                    db.scrapedStreamDao().deleteStreamById(cacheKey)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from Room cache: ${e.message}")
        }

        val cleanTitle = sanitizeTitle(title)

        Log.d(TAG, "Starting Exact High-Power Stream Extraction for: $cleanTitle (TMDB: $tmdbId, isTv: $isTv, isAnime: $isAnime)")

        // 0. High-Speed Anime API Resolver (Direct HLS M3U8 with CORS Proxy)
        if (isAnime || tmdbId.startsWith("anikoto_")) {
            try {
                val lookupKey = if (title.startsWith("http") || title.contains("anikoto.cz") || title.contains("/watch/")) {
                    title
                } else if (cleanTitle.isNotBlank()) {
                    cleanTitle
                } else {
                    title
                }
                Log.d(TAG, "Tier 0: Querying Anime API Resolver for $lookupKey (S$season Ep$effectiveEpisode)...")
                val animeStream = AnikotoScraper.getStreamByTitle(
                    title = lookupKey,
                    season = season,
                    episode = effectiveEpisode
                )
                if (animeStream != null && animeStream.streamUrl.isNotEmpty()) {
                    Log.d(TAG, "Tier 0: Anime stream resolved successfully via API: ${animeStream.streamUrl}")
                    streamCache[cacheKey] = animeStream
                    saveToRoomCache(context, cacheKey, animeStream)
                    return animeStream
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 0 Anime API resolver failed: ${e.message}")
            }
        }

        var finalTmdbId = tmdbId.trim()
        var effectiveIsTv = if (isAnime && !cleanTitle.lowercase().contains("movie")) true else isTv
        var resolvedImdbId: String? = if (finalTmdbId.startsWith("tt")) finalTmdbId else null

        if (finalTmdbId.startsWith("movie_")) {
            finalTmdbId = finalTmdbId.removePrefix("movie_")
            effectiveIsTv = false
        } else if (finalTmdbId.startsWith("series_")) {
            finalTmdbId = finalTmdbId.removePrefix("series_")
            effectiveIsTv = true
        }

        if (finalTmdbId.startsWith("tt")) {
            try {
                Log.d(TAG, "Resolving IMDb ID $finalTmdbId to TMDB numeric ID...")
                val findRes = com.example.data.network.RetrofitClient.tmdbApi.getByExternalId(finalTmdbId, "imdb_id")
                val tv = findRes.tv_results?.firstOrNull()
                val movie = findRes.movie_results?.firstOrNull()
                if (tv != null && !effectiveIsTv && movie == null) {
                    finalTmdbId = tv.id.toString()
                    effectiveIsTv = true
                    Log.d(TAG, "Resolved IMDb ID to TV show TMDB ID: $finalTmdbId")
                } else if (movie != null) {
                    finalTmdbId = movie.id.toString()
                    effectiveIsTv = false
                    Log.d(TAG, "Resolved IMDb ID to Movie TMDB ID: $finalTmdbId")
                } else if (tv != null) {
                    finalTmdbId = tv.id.toString()
                    effectiveIsTv = true
                    Log.d(TAG, "Resolved IMDb ID to TV show TMDB ID: $finalTmdbId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve IMDb ID $finalTmdbId: ${e.message}")
            }
        }

        if (finalTmdbId.startsWith("anikoto_") || (!finalTmdbId.all { it.isDigit() } && cleanTitle.isNotBlank())) {
            try {
                val slug = if (finalTmdbId.startsWith("anikoto_")) finalTmdbId.substringAfter("anikoto_") else ""
                var cleanQuery = slug.replace("-", " ").replace(Regex("""\d+$"""), "").trim()
                if (cleanQuery.isEmpty()) {
                    cleanQuery = cleanTitle
                }
                Log.d(TAG, "Resolving TMDB ID for title query: $cleanQuery")
                var firstResult = if (effectiveIsTv) {
                    com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = cleanQuery).results?.firstOrNull()
                } else {
                    com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = cleanQuery).results?.firstOrNull()
                }
                if (firstResult == null) {
                    firstResult = if (effectiveIsTv) {
                        com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = cleanQuery).results?.firstOrNull()?.also { effectiveIsTv = false }
                    } else {
                        com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = cleanQuery).results?.firstOrNull()?.also { effectiveIsTv = true }
                    }
                }
                if (firstResult == null && cleanTitle.isNotBlank() && cleanQuery != cleanTitle) {
                    firstResult = com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = cleanTitle).results?.firstOrNull()?.also { effectiveIsTv = true }
                        ?: com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = cleanTitle).results?.firstOrNull()?.also { effectiveIsTv = false }
                }
                if (firstResult != null) {
                    finalTmdbId = firstResult.id.toString()
                    Log.d(TAG, "Resolved TMDB ID from query $cleanQuery -> $finalTmdbId (isTv: $effectiveIsTv)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search TMDB ID for $cleanTitle: ${e.message}")
            }
        }

        // PARALLEL HIGH POWER MULTI-SERVER RACING ENGINE:
        // Execute 5 Concurrent Engines: [VidLink] + [VidSrc Multi-Host] + [AutoEmbed/Smashy/2Embed] + [VidNest 12-Sub] + [VidRock Deep]
        return coroutineScope {
            Log.d(TAG, "Launching High-Power Multi-Server Concurrent Scrapers for TMDB ID: $finalTmdbId (IMDb: $resolvedImdbId)...")
            val resultChannel = kotlinx.coroutines.channels.Channel<ScrapedStreamResult>(12)

            // Task 1: VidLink Native Engine (vidlink.pro / api / sources)
            val vidlinkJob = launch(Dispatchers.IO) {
                try {
                    val res = VidLinkNativeScraper.extractStream(
                        tmdbId = finalTmdbId,
                        isTv = effectiveIsTv,
                        season = season,
                        episode = episode,
                        imdbId = resolvedImdbId
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidLink scraper WINNER: ${res.streamUrl}")
                        resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidLink task error: ${e.message}")
                }
            }

            // Task 2: VidSrc Multi-Host Engine (vidsrc.me, vidsrc.to, vidsrc.xyz, vidsrc.vip, vidsrc.cc, vidsrc.pm, etc.)
            val vidsrcJob = launch(Dispatchers.IO) {
                try {
                    val res = VidSrcNativeScraper.extractStream(
                        tmdbId = finalTmdbId,
                        isTv = effectiveIsTv,
                        season = season,
                        episode = episode,
                        imdbId = resolvedImdbId
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidSrc scraper WINNER: ${res.streamUrl}")
                        resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidSrc task error: ${e.message}")
                }
            }

            // Task 3: AutoEmbed & Multi-Source Engine (AutoEmbed, 2Embed, SmashyStream, MultiEmbed, Videasy)
            val autoEmbedJob = launch(Dispatchers.IO) {
                try {
                    val res = AutoEmbedNativeScraper.extractStream(
                        tmdbId = finalTmdbId,
                        isTv = effectiveIsTv,
                        season = season,
                        episode = episode,
                        imdbId = resolvedImdbId
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "AutoEmbed scraper WINNER: ${res.streamUrl}")
                        resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AutoEmbed task error: ${e.message}")
                }
            }

            // Task 4: VidNest Concurrent Multi-Provider Scraper (Fastest among 12 sub-providers)
            val vidnestJob = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStream(
                        tmdbId = finalTmdbId,
                        isTv = effectiveIsTv,
                        season = season,
                        episode = episode
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidNest scraper WINNER: ${res.streamUrl}")
                        resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidNest task error: ${e.message}")
                }
            }

            // Task 5: VidRock Architecture Deep Scraper (vidrock.ru / vidrock.net / dynamic proxies)
            val vidrockJob = launch(Dispatchers.IO) {
                try {
                    val res = VidrockNativeScraper.extractStream(
                        tmdbId = finalTmdbId,
                        isTv = effectiveIsTv,
                        season = season,
                        episode = episode
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidRock scraper WINNER: ${res.streamUrl}")
                        resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidRock task error: ${e.message}")
                }
            }

            var winningStream: ScrapedStreamResult? = null
            try {
                winningStream = kotlinx.coroutines.withTimeoutOrNull(28000L) {
                    resultChannel.receive()
                }
            } catch (_: Exception) {}

            vidlinkJob.cancel()
            vidsrcJob.cancel()
            autoEmbedJob.cancel()
            vidnestJob.cancel()
            vidrockJob.cancel()

            if (winningStream != null && winningStream.streamUrl.isNotBlank()) {
                Log.d(TAG, "Multi-Server Winning Stream selected: ${winningStream.streamUrl}")
                streamCache[cacheKey] = winningStream
                saveToRoomCache(context, cacheKey, winningStream)
                return@coroutineScope winningStream
            }

            // Fallback 1: MovieBox Native Scraper if exact ID scrapers missed
            try {
                Log.d(TAG, "Tier 2 Fallback: Querying MovieBox for $cleanTitle...")
                val subjectId = MovieBoxNativeScraper.searchSubjectId(cleanTitle)
                if (!subjectId.isNullOrEmpty()) {
                    val res = MovieBoxNativeScraper.getStreamInfo(
                        subjectId = subjectId,
                        season = if (effectiveIsTv) season else 0,
                        episode = if (effectiveIsTv) episode else 0
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        streamCache[cacheKey] = res
                        saveToRoomCache(context, cacheKey, res)
                        return@coroutineScope res
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MovieBox fallback error: ${e.message}")
            }

            // Fallback 2: Off-Screen Headless In-App Web Extractor
            try {
                Log.d(TAG, "Tier 3: Launching In-App Headless Web Scraper for $finalTmdbId...")
                val webStream = InAppHeadlessWebScraper.extractWebStream(
                    context = context,
                    tmdbId = "$finalTmdbId",
                    isTv = effectiveIsTv,
                    season = season,
                    episode = episode,
                    imdbId = resolvedImdbId
                )
                if (webStream != null && webStream.streamUrl.isNotEmpty()) {
                    Log.d(TAG, "Tier 3: In-App Headless Scraper resolved stream successfully!")
                    streamCache[cacheKey] = webStream
                    saveToRoomCache(context, cacheKey, webStream)
                    return@coroutineScope webStream
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 3 Headless Web scraper failed: ${e.message}")
            }

            // Fallback 3: Stream Cloud Relay
            try {
                Log.d(TAG, "Tier 4: Querying Fallback Stream Scraper...")
                val fallback = queryFallbackApi(finalTmdbId, effectiveIsTv, season, episode)
                if (fallback != null) {
                    Log.d(TAG, "Tier 4: Fallback stream resolved successfully!")
                    streamCache[cacheKey] = fallback
                    saveToRoomCache(context, cacheKey, fallback)
                    return@coroutineScope fallback
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 4 fallback failed: ${e.message}")
            }

            Log.w(TAG, "All extraction tiers completed without finding a direct stream.")
            null
        }
    }

    suspend fun getStream(
        context: Context,
        title: String,
        tmdbId: String,
        mediaType: String,
        season: Int = 1,
        episode: Int = 1,
        isAnime: Boolean = false
    ): ScrapedStreamResult? = getStream(
        context = context,
        title = title,
        tmdbId = tmdbId,
        isTv = !mediaType.equals("movie", ignoreCase = true),
        season = season,
        episode = episode,
        isAnime = isAnime
    )

    private fun mapToJson(map: Map<String, String>): String {
        val json = org.json.JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun jsonToMap(jsonStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val json = org.json.JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.getString(key)
            }
        } catch (_: Exception) {}
        return map
    }

    private suspend fun saveToRoomCache(context: Context, key: String, result: ScrapedStreamResult) {
        try {
            val db = com.example.data.database.AppDatabase.getDatabase(context)
            val entity = com.example.data.database.ScrapedStreamEntity(
                id = key,
                streamUrl = result.streamUrl,
                headersJson = mapToJson(result.headers),
                referer = result.referer,
                timestamp = System.currentTimeMillis()
            )
            db.scrapedStreamDao().insertStream(entity)
            Log.d(TAG, "Saved resolved stream to local Room cache for key: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save stream to Room cache: ${e.message}")
        }
    }

    suspend fun getStreamForMovie(context: Context, title: String, tmdbId: Int): ScrapedStreamResult? {
        return getStream(context, title, tmdbId.toString(), isTv = false)
    }

    suspend fun getStreamForMovie(context: Context, title: String, tmdbId: String): ScrapedStreamResult? {
        return getStream(context, title, tmdbId, isTv = false)
    }

    private suspend fun queryFallbackApi(
        tmdbId: String,
        isTv: Boolean,
        season: Int,
        episode: Int
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val endpointUrl = if (!isTv) {
            "https://universal-stream-scraper-production.up.railway.app/api/moviebox/play?id=$tmdbId&type=movie"
        } else {
            "https://universal-stream-scraper-production.up.railway.app/api/moviebox/play?id=$tmdbId&type=tv&s=$season&e=$episode"
        }

        try {
            val request = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotEmpty()) {
                val trimmed = body.trim()
                val streamUrl = if (trimmed.startsWith("http")) {
                    trimmed
                } else if (trimmed.startsWith("{")) {
                    val json = JSONObject(trimmed)
                    json.optString("url", json.optString("stream", json.optString("streamUrl", "")))
                } else ""

                if (streamUrl.isNotEmpty()) {
                    return@withContext ScrapedStreamResult(
                        streamUrl = streamUrl,
                        headers = mapOf(
                            "User-Agent" to NetworkModule.USER_AGENT,
                            "Referer" to "https://vidlink.pro/",
                            "Origin" to "https://vidlink.pro"
                        ),
                        referer = "https://vidlink.pro/"
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        null
    }

    private fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("[:\\-–—_/\\[\\]()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
