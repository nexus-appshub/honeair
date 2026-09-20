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
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun resolveRelativeUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }
        return try {
            val baseUri = java.net.URI(baseUrl)
            baseUri.resolve(relativeUrl).toString()
        } catch (_: Exception) {
            if (relativeUrl.startsWith("/")) {
                val scheme = if (baseUrl.startsWith("https")) "https" else "http"
                val host = baseUrl.substringAfter("://").substringBefore("/")
                "$scheme://$host$relativeUrl"
            } else {
                val baseDir = baseUrl.substringBeforeLast("/")
                "$baseDir/$relativeUrl"
            }
        }
    }

    private suspend fun verifyStreamAlive(url: String, headers: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext false
        try {
            val reqBuilder = Request.Builder().url(url)
            
            // Standard browser-mimicking headers to prevent Cloudflare/WAF blockages
            reqBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            reqBuilder.addHeader("Accept", "*/*")
            reqBuilder.addHeader("Accept-Language", "en-US,en;q=0.9")
            
            headers.forEach { (k, v) -> 
                reqBuilder.header(k, v)
            }
            
            val req = reqBuilder.build()
            val resp = httpClient.newCall(req).execute()
            val code = resp.code
            if (code != 200 && code != 206) {
                resp.close()
                return@withContext false
            }

            val isM3u8 = url.contains(".m3u8", ignoreCase = true) ||
                         url.contains(".txt", ignoreCase = true) ||
                         (resp.header("Content-Type")?.contains("mpegurl", ignoreCase = true) == true)

            if (isM3u8) {
                val source = resp.body?.source() ?: run { resp.close(); return@withContext false }
                source.request(8192)
                val bodyStr = source.buffer.clone().readUtf8()
                resp.close()

                if (!bodyStr.contains("#EXTM3U")) return@withContext false
                if (bodyStr.contains("404") || bodyStr.contains("Video not found") || bodyStr.contains("Access Denied") || bodyStr.contains("error")) return@withContext false

                return@withContext true
            } else {
                resp.close()
                return@withContext true
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

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

        // 0. High-Speed Anime Native & API Resolver (Direct Native Extraction + HLS M3U8)
        val isItemAnime = isAnime || tmdbId.startsWith("anikoto_") || tmdbId.startsWith("al_") || tmdbId.startsWith("mal_") || AnimePosterEngine.isAnime(title = title, id = tmdbId)
        if (isItemAnime) {
            try {
                val slugKey = when {
                    tmdbId.startsWith("anikoto_") -> tmdbId.removePrefix("anikoto_")
                    tmdbId.contains("-") && (tmdbId.any { it.isDigit() } || tmdbId.length > 5) -> tmdbId
                    title.contains("-") && title.any { it.isDigit() } && !title.contains(" ") -> title
                    else -> ""
                }
                val lookupKey = if (title.startsWith("http") || title.contains("anikoto.cz") || title.contains("/watch/")) {
                    title
                } else if (cleanTitle.isNotBlank()) {
                    cleanTitle
                } else {
                    title
                }
                Log.d(TAG, "Tier 0: Querying In-App Native Scraper & Anime API for $lookupKey / slug: $slugKey (S$season Ep$effectiveEpisode)...")
                
                // Priority 1: High-Speed Direct API Stream with Subtitles
                var animeStream = if (slugKey.isNotBlank()) {
                    AnikotoScraper.getStreamByTitle(
                        title = slugKey,
                        season = season,
                        episode = effectiveEpisode
                    )
                } else null

                if (animeStream == null || animeStream.streamUrl.isEmpty()) {
                    animeStream = AnikotoScraper.getStreamByTitle(
                        title = lookupKey,
                        season = season,
                        episode = effectiveEpisode
                    )
                }

                if (animeStream != null && animeStream.streamUrl.isNotEmpty()) {
                    Log.d(TAG, "Tier 0: Anime stream resolved successfully via API: ${animeStream.streamUrl}")
                    streamCache[cacheKey] = animeStream
                    saveToRoomCache(context, cacheKey, animeStream)
                    return animeStream
                }

                // Priority 2: In-app native extraction
                val nativeTarget = if (slugKey.isNotBlank()) slugKey else lookupKey
                val nativeStream = UniversalAnimeDownloadScraper.extractNativeAnimeStream(
                    title = nativeTarget,
                    season = season,
                    episode = effectiveEpisode
                )
                if (nativeStream != null && nativeStream.streamUrl.isNotEmpty()) {
                    Log.d(TAG, "Tier 0: Anime stream resolved via Native In-App Scraper: ${nativeStream.streamUrl}")
                    streamCache[cacheKey] = nativeStream
                    saveToRoomCache(context, cacheKey, nativeStream)
                    return nativeStream
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 0 Anime resolver failed: ${e.message}")
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

        var effectiveSeason = season
        if (finalTmdbId.startsWith("anikoto_") || (!finalTmdbId.all { it.isDigit() } && cleanTitle.isNotBlank())) {
            try {
                val slug = if (finalTmdbId.startsWith("anikoto_")) finalTmdbId.substringAfter("anikoto_") else ""
                var cleanQuery = slug.replace("-", " ").replace(Regex("""\d+$"""), "").trim()
                if (cleanQuery.isEmpty()) {
                    cleanQuery = cleanTitle
                }

                // Extract season number from title if season == 1
                val detectedSeasonMatch = Regex("""(?i)(?:season|s)\s*(\d+)""").find("$cleanTitle $cleanQuery")
                val detectedSeason = detectedSeasonMatch?.groupValues?.get(1)?.toIntOrNull()
                if (detectedSeason != null && detectedSeason > 1 && season == 1) {
                    effectiveSeason = detectedSeason
                    Log.d(TAG, "Detected Season $effectiveSeason from title")
                }

                // Strip season / arc noise from cleanQuery for better TMDB hit rate
                val baseQuery = cleanQuery.replace(Regex("""(?i)(?:season|part|cour|arc|s)\s*\d+.*"""), "").trim()
                val words = baseQuery.split(" ").filter { it.isNotBlank() }
                val shortBaseQuery = if (words.size >= 2) words.take(2).joinToString(" ") else baseQuery

                val queriesToTry = listOf(cleanQuery, baseQuery, cleanTitle, shortBaseQuery).filter { it.isNotBlank() }.distinct()
                
                var firstResult: com.example.data.network.TmdbMediaResult? = null
                for (q in queriesToTry) {
                    Log.d(TAG, "Resolving TMDB ID for title query: $q")
                    firstResult = if (effectiveIsTv) {
                        com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = q).results?.firstOrNull()
                    } else {
                        com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = q).results?.firstOrNull()
                    }
                    if (firstResult == null) {
                        firstResult = if (effectiveIsTv) {
                            com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = q).results?.firstOrNull()?.also { effectiveIsTv = false }
                        } else {
                            com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = q).results?.firstOrNull()?.also { effectiveIsTv = true }
                        }
                    }
                    if (firstResult != null) break
                }

                if (firstResult == null) {
                    // Fuzzy / Typo Fallback: Search with the longest, most significant words
                    val cleanWords = baseQuery.split(" ")
                        .map { it.trim().replace(Regex("[^a-zA-Z0-9]"), "") }
                        .filter { it.length > 3 && it.lowercase() !in listOf("season", "episode", "legacy", "series", "movie", "show", "part") }
                    if (cleanWords.isNotEmpty()) {
                        val longestWordQuery = cleanWords.sortedByDescending { it.length }.take(2).joinToString(" ")
                        Log.d(TAG, "Typo fallback: searching TMDB with longest words query: $longestWordQuery")
                        firstResult = if (effectiveIsTv) {
                            com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = longestWordQuery).results?.firstOrNull()
                        } else {
                            com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = longestWordQuery).results?.firstOrNull()
                        }
                        if (firstResult == null) {
                            firstResult = if (effectiveIsTv) {
                                com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = longestWordQuery).results?.firstOrNull()?.also { effectiveIsTv = false }
                            } else {
                                com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = longestWordQuery).results?.firstOrNull()?.also { effectiveIsTv = true }
                            }
                        }
                    }
                }

                if (firstResult != null) {
                    finalTmdbId = firstResult.id.toString()
                    Log.d(TAG, "Resolved TMDB ID from search -> $finalTmdbId (isTv: $effectiveIsTv, season: $effectiveSeason)")
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
                        season = effectiveSeason,
                        episode = episode,
                        imdbId = resolvedImdbId
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidLink scraper WINNER: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res) else Log.w(TAG, "Stream verification failed for: ${res.streamUrl}")
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
                        season = effectiveSeason,
                        episode = episode,
                        imdbId = resolvedImdbId
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidSrc scraper WINNER: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res) else Log.w(TAG, "Stream verification failed for: ${res.streamUrl}")
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
                        season = effectiveSeason,
                        episode = episode,
                        imdbId = resolvedImdbId
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "AutoEmbed scraper WINNER: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res) else Log.w(TAG, "Stream verification failed for: ${res.streamUrl}")
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
                        season = effectiveSeason,
                        episode = episode
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidNest scraper WINNER: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res) else Log.w(TAG, "Stream verification failed for: ${res.streamUrl}")
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
                        season = effectiveSeason,
                        episode = episode
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidRock scraper WINNER: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res) else Log.w(TAG, "Stream verification failed for: ${res.streamUrl}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidRock task error: ${e.message}")
                }
            }

            // Task 6: MovieBox Concurrent Deep Extractor (Apuseen / Hakunamatata / Aoneroom)
            val movieBoxJob = launch(Dispatchers.IO) {
                try {
                    val subjectId = MovieBoxNativeScraper.searchSubjectId(cleanTitle) ?: MovieBoxNativeScraper.searchSubjectId(finalTmdbId)
                    if (!subjectId.isNullOrEmpty()) {
                        val res = MovieBoxNativeScraper.getStreamInfo(
                            subjectId = subjectId,
                            season = if (effectiveIsTv) effectiveSeason else 0,
                            episode = if (effectiveIsTv) episode else 0
                        )
                        if (res != null && res.streamUrl.isNotBlank()) {
                            Log.d(TAG, "MovieBox scraper WINNER: ${res.streamUrl}")
                            if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res) else Log.w(TAG, "Stream verification failed for: ${res.streamUrl}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MovieBox task error: ${e.message}")
                }
            }

            var winningStream: ScrapedStreamResult? = null
            try {
                winningStream = kotlinx.coroutines.withTimeoutOrNull(55000L) {
                    resultChannel.receive()
                }
            } catch (_: Exception) {}

            vidlinkJob.cancel()
            vidsrcJob.cancel()
            autoEmbedJob.cancel()
            vidnestJob.cancel()
            vidrockJob.cancel()
            movieBoxJob.cancel()

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
                        season = if (effectiveIsTv) effectiveSeason else 0,
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
                    season = effectiveSeason,
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
                val fallback = queryFallbackApi(finalTmdbId, effectiveIsTv, effectiveSeason, episode)
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

    data class StreamRaceWinner(
        val serverKey: String,
        val serverName: String,
        val result: ScrapedStreamResult
    )

    suspend fun raceFastestServerStream(
        context: Context,
        tmdbId: String,
        title: String,
        isTv: Boolean,
        season: Int,
        episode: Int,
        preferredServerKey: String? = null,
        isAnime: Boolean = false
    ): StreamRaceWinner? = withContext(Dispatchers.IO) {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val cacheKey = "$tmdbId-$season-$effectiveEpisode"

        // 1. Check in-memory cache if no specific server is forced
        if (preferredServerKey == null || preferredServerKey == "fastest_auto") {
            streamCache[cacheKey]?.let {
                if (it.streamUrl.isNotBlank()) {
                    return@withContext StreamRaceWinner("fastest_auto", "Fastest Direct", it)
                }
            }
        }

        // 2. Resolve numeric TMDB ID & clean title once upfront
        var finalTmdbId = VidnestNativeScraper.resolveToNumericTmdbId(tmdbId, isTv)
        if (!finalTmdbId.all { it.isDigit() }) {
            try {
                val q = title.replace(Regex("""(?i)(?:season|part|cour|arc|s)\s*\d+.*"""), "").trim()
                val sr = if (isTv) {
                    com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = q).results?.firstOrNull()
                } else {
                    com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = q).results?.firstOrNull()
                }
                if (sr != null) {
                    finalTmdbId = sr.id.toString()
                }
            } catch (_: Exception) {}
        }

        // 3. If user selected a specific provider (e.g., Hindi, VidRock, Flixer, Prime, Hexa)
        if (!preferredServerKey.isNullOrBlank() && preferredServerKey != "fastest_auto") {
            try {
                val specificResult: ScrapedStreamResult? = when (preferredServerKey) {
                    "vidrock_direct" -> VidrockNativeScraper.extractStream(finalTmdbId, isTv, season, effectiveEpisode)
                    "vidlink_direct" -> VidLinkNativeScraper.extractStream(finalTmdbId, isTv, season, effectiveEpisode)
                    "autoembed_direct" -> AutoEmbedNativeScraper.extractStream(finalTmdbId, isTv, season, effectiveEpisode)
                    else -> VidnestNativeScraper.extractStreamFromProvider(preferredServerKey, finalTmdbId, isTv, season, effectiveEpisode)
                }
                if (specificResult != null && specificResult.streamUrl.isNotBlank()) {
                    val serverName = when (preferredServerKey) {
                        "delta" -> "HINDI"
                        "vidrock_direct" -> "ZOZO (Direct)"
                        "filxer" -> "Flixer"
                        "prime" -> "Prime"
                        "hexa" -> "Hexa"
                        "alfa" -> "Alfa"
                        "gama" -> "Gama"
                        "zeta" -> "Zeta"
                        "lamda" -> "Lamda"
                        "catflix" -> "Catflix"
                        else -> preferredServerKey.uppercase()
                    }
                    streamCache[cacheKey] = specificResult
                    saveToRoomCache(context, cacheKey, specificResult)
                    return@withContext StreamRaceWinner(preferredServerKey, serverName, specificResult)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Preferred server $preferredServerKey failed: ${e.message}")
            }
        }

        // 4. PARALLEL SPEED RACE ACROSS MULTI LIST OF SERVERS:
        // Hindi, VidRock (direct), Flixer, Prime, Hexa, Alfa, Gama, VidLink, AutoEmbed
        coroutineScope {
            val winnerChannel = kotlinx.coroutines.channels.Channel<StreamRaceWinner>(15)

            // Server 1: VidRock Direct
            val jVidrock = launch(Dispatchers.IO) {
                try {
                    val res = VidrockNativeScraper.extractStream(finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("vidrock_direct", "ZOZO (Direct)", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 2: Flixer (Rogflix direct)
            val jFlixer = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("filxer", finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("filxer", "Flixer", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 3: Prime (Vidrock endpoint)
            val jPrime = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("prime", finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("prime", "Prime", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 4: Hexa (Vidlink endpoint)
            val jHexa = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("hexa", finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("hexa", "Hexa", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 5: HINDI (Delta endpoint)
            val jHindi = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("delta", finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("delta", "HINDI", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 6: Alfa
            val jAlfa = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("alfa", finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("alfa", "Alfa", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 7: Gama
            val jGama = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("gama", finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("gama", "Gama", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 8: VidLink direct
            val jVidlink = launch(Dispatchers.IO) {
                try {
                    val res = VidLinkNativeScraper.extractStream(finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("vidlink_direct", "VidLink", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 9: AutoEmbed direct
            val jAutoEmbed = launch(Dispatchers.IO) {
                try {
                    val res = AutoEmbedNativeScraper.extractStream(finalTmdbId, isTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        winnerChannel.trySend(StreamRaceWinner("autoembed_direct", "AutoEmbed", res))
                    }
                } catch (_: Exception) {}
            }

            // Server 10: Anikoto Anime
            val jAnikoto = if (isAnime || com.example.scraper.AnimePosterEngine.isAnime(title, "", "series", tmdbId)) {
                launch(Dispatchers.IO) {
                    try {
                        val cleanTitle = title.replace(Regex("""(?i)(?:season|part|cour|arc|s)\s*\d+.*"""), "").trim()
                        val slugKey = cleanTitle.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")
                        var res = AnikotoScraper.getStreamByTitle(slugKey, season, effectiveEpisode)
                        if (res == null || res.streamUrl.isBlank()) {
                            res = AnikotoScraper.getStreamByTitle(cleanTitle, season, effectiveEpisode)
                        }
                        if (res != null && res.streamUrl.isNotBlank()) {
                            winnerChannel.trySend(StreamRaceWinner("anikoto", "Anime (Anikoto)", res))
                        }
                    } catch (_: Exception) {}
                }
            } else null

            // Server 11: Universal Anime Native Scraper
            val jUniversalAnime = if (isAnime || com.example.scraper.AnimePosterEngine.isAnime(title, "", "series", tmdbId)) {
                launch(Dispatchers.IO) {
                    try {
                        val cleanTitle = title.replace(Regex("""(?i)(?:season|part|cour|arc|s)\s*\d+.*"""), "").trim()
                        val slugKey = cleanTitle.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")
                        val nativeTarget = if (slugKey.isNotBlank()) slugKey else cleanTitle
                        val res = UniversalAnimeDownloadScraper.extractNativeAnimeStream(
                            title = nativeTarget,
                            season = season,
                            episode = effectiveEpisode
                        )
                        if (res != null && res.streamUrl.isNotBlank()) {
                            winnerChannel.trySend(StreamRaceWinner("native_anime", "Anime (Fast)", res))
                        }
                    } catch (_: Exception) {}
                }
            } else null

            val allJobs = listOfNotNull(jVidrock, jFlixer, jPrime, jHexa, jHindi, jAlfa, jGama, jVidlink, jAutoEmbed, jAnikoto, jUniversalAnime)

            var winner: StreamRaceWinner? = null
            try {
                winner = kotlinx.coroutines.withTimeoutOrNull(8500L) {
                    winnerChannel.receive()
                }
            } catch (_: Exception) {}

            allJobs.forEach { it.cancel() }

            if (winner != null && winner.result.streamUrl.isNotBlank()) {
                Log.d(TAG, "Fastest Parallel Race Winner: [${winner.serverName}] -> ${winner.result.streamUrl}")
                streamCache[cacheKey] = winner.result
                saveToRoomCache(context, cacheKey, winner.result)
                return@coroutineScope winner
            }

            // Fallback to standard getStream if race timed out
            val fallback = getStream(
                context = context,
                title = title,
                tmdbId = finalTmdbId,
                isTv = isTv,
                season = season,
                episode = effectiveEpisode,
                isAnime = false
            )
            if (fallback != null && fallback.streamUrl.isNotBlank()) {
                return@coroutineScope StreamRaceWinner("fallback", "Fastest Direct", fallback)
            }
            null
        }
    }
}
