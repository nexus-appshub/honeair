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
    private data class TimestampedStream(val result: ScrapedStreamResult, val timestamp: Long = System.currentTimeMillis())
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, TimestampedStream>()
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
            val contentType = resp.header("Content-Type") ?: ""
            if (contentType.contains("text/html", ignoreCase = true)) {
                resp.close()
                return@withContext false
            }

            if (code in 200..299 || code == 206) {
                val isM3u8 = url.contains(".m3u8", ignoreCase = true) ||
                             url.contains(".txt", ignoreCase = true) ||
                             contentType.contains("mpegurl", ignoreCase = true)

                if (isM3u8) {
                    val source = resp.body?.source() ?: run { resp.close(); return@withContext false }
                    source.request(2048)
                    val bodyStr = source.buffer.clone().readUtf8()
                    resp.close()

                    if (bodyStr.contains("<html", ignoreCase = true) || bodyStr.contains("<!DOCTYPE", ignoreCase = true)) {
                        return@withContext false
                    }
                    return@withContext bodyStr.contains("#EXTM3U") || bodyStr.contains("#EXT-X-") || bodyStr.contains("#EXTINF")
                } else {
                    resp.close()
                    return@withContext true
                }
            } else {
                resp.close()
                return@withContext false
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    private val blacklistedUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun invalidateCache(tmdbId: String, season: Int = 1, episode: Int = 1, failedUrl: String? = null) {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val key = "$tmdbId-$season-$effectiveEpisode"
        streamCache.remove(key)
        streamCache.remove("$tmdbId-$season-$episode")
        if (!failedUrl.isNullOrBlank()) {
            blacklistedUrls.add(failedUrl)
        }
    }

    fun getCachedStream(tmdbId: String, season: Int = 1, episode: Int = 1): ScrapedStreamResult? {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val key = "$tmdbId-$season-$effectiveEpisode"
        val entry = streamCache[key] ?: streamCache["$tmdbId-$season-$episode"]
        if (entry != null && (System.currentTimeMillis() - entry.timestamp < 15 * 60 * 1000L) && !blacklistedUrls.contains(entry.result.streamUrl)) {
            return entry.result
        }
        return null
    }

    fun cacheStream(tmdbId: String, season: Int = 1, episode: Int = 1, result: ScrapedStreamResult) {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val key = "$tmdbId-$season-$effectiveEpisode"
        streamCache[key] = TimestampedStream(result)
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
        val memCached = streamCache[cacheKey]
        if (memCached != null && System.currentTimeMillis() - memCached.timestamp < 15 * 60 * 1000L && !blacklistedUrls.contains(memCached.result.streamUrl)) {
            if (memCached.result.streamUrl.isNotBlank()) return memCached.result
        }

        // Try to load from Local Room Cache first to prevent redundant scraping
        try {
            val db = com.example.data.database.AppDatabase.getDatabase(context)
            val cachedEntity = db.scrapedStreamDao().getStreamById(cacheKey)
            if (cachedEntity != null) {
                // Check if it is fresh (within 30 minutes)
                if (System.currentTimeMillis() - cachedEntity.timestamp < 30 * 60 * 1000L) {
                    val headers = jsonToMap(cachedEntity.headersJson)
                    val result = ScrapedStreamResult(
                        streamUrl = cachedEntity.streamUrl,
                        headers = headers,
                        referer = cachedEntity.referer
                    )
                    streamCache[cacheKey] = TimestampedStream(result)
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
                    streamCache[cacheKey] = TimestampedStream(animeStream)
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
                    streamCache[cacheKey] = TimestampedStream(nativeStream)
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
        // Priority 1 (Default): Vidnest.fun Deep Multi-Sub-Provider Parallel Scraper
        // Sub-servers: [Beta, Filxer/HM VIP, Gama, Alfa, Hindi/Delta, Zeta, Ophim, Catflix, Sigma, Prime, Hexa, Lamda]
        val vidnestResult = try {
            VidnestNativeScraper.extractStream(
                tmdbId = finalTmdbId,
                isTv = effectiveIsTv,
                season = effectiveSeason,
                episode = episode
            )
        } catch (e: Exception) {
            Log.w(TAG, "VidNest primary extraction error: ${e.message}")
            null
        }

        if (vidnestResult != null && vidnestResult.streamUrl.isNotBlank() && verifyStreamAlive(vidnestResult.streamUrl, vidnestResult.headers)) {
            Log.d(TAG, "Primary Vidnest.fun resolved direct stream successfully: ${vidnestResult.streamUrl}")
            streamCache[cacheKey] = TimestampedStream(vidnestResult)
            saveToRoomCache(context, cacheKey, vidnestResult)
            return vidnestResult
        }

        // Priority 2 (Fallback): Secondary Deep Scraping Tier
        // If content is not on vidnest.fun -> Deep scrape [VidRock] + [VidSrc Multi-Host / sbs] + [VidLink.to / pro] + [AutoEmbed]
        return coroutineScope {
            Log.d(TAG, "Vidnest not found or timed out. Launching Secondary Deep Scrapers for TMDB ID: $finalTmdbId (IMDb: $resolvedImdbId)...")
            val resultChannel = kotlinx.coroutines.channels.Channel<ScrapedStreamResult>(8)

            // Task 1: VidRock Architecture Deep Scraper (vidrock.ru / vidrock.net / vidsrc architecture)
            val vidrockJob = launch(Dispatchers.IO) {
                try {
                    val res = VidrockNativeScraper.extractStream(
                        tmdbId = finalTmdbId,
                        isTv = effectiveIsTv,
                        season = effectiveSeason,
                        episode = episode
                    )
                    if (res != null && res.streamUrl.isNotBlank()) {
                        Log.d(TAG, "VidRock secondary winner: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidRock task error: ${e.message}")
                }
            }

            // Task 2: VidSrc Multi-Host Deep Engine (vidsrc.sbs, vidsrc.me, vidsrc.cc, vidsrc.to, vidsrc.pm, etc.)
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
                        Log.d(TAG, "VidSrc secondary winner: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidSrc task error: ${e.message}")
                }
            }

            // Task 3: VidLink Native Engine (vidlink.to / vidlink.pro)
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
                        Log.d(TAG, "VidLink secondary winner: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VidLink task error: ${e.message}")
                }
            }

            // Task 4: AutoEmbed & Multi-Source Engine (AutoEmbed, SmashyStream, 2Embed, Videasy)
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
                        Log.d(TAG, "AutoEmbed secondary winner: ${res.streamUrl}")
                        if (verifyStreamAlive(res.streamUrl, res.headers)) resultChannel.trySend(res)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AutoEmbed task error: ${e.message}")
                }
            }

            var winningStream: ScrapedStreamResult? = null
            try {
                winningStream = kotlinx.coroutines.withTimeoutOrNull(20000L) {
                    resultChannel.receive()
                }
            } catch (_: Exception) {}

            vidrockJob.cancel()
            vidsrcJob.cancel()
            vidlinkJob.cancel()
            autoEmbedJob.cancel()

            if (winningStream != null && winningStream.streamUrl.isNotBlank()) {
                Log.d(TAG, "Secondary Deep Scraping Winning Stream selected: ${winningStream.streamUrl}")
                streamCache[cacheKey] = TimestampedStream(winningStream)
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
                        streamCache[cacheKey] = TimestampedStream(res)
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
                    streamCache[cacheKey] = TimestampedStream(webStream)
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
                    streamCache[cacheKey] = TimestampedStream(fallback)
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

    data class VerifiedStreamServer(
        val key: String,
        val name: String,
        val type: String = "DIRECT", // "DIRECT", "SUB", "DUB", "MULTI"
        val accentColorHex: Long = 0xFF00E5FF,
        val result: ScrapedStreamResult
    )

    private val verifiedServersMap = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<VerifiedStreamServer>>()
    private val verifiedServerDirectCache = java.util.concurrent.ConcurrentHashMap<String, ScrapedStreamResult>()

    fun getVerifiedServers(tmdbId: String, season: Int = 1, episode: Int = 1): List<VerifiedStreamServer> {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val key = "$tmdbId-$season-$effectiveEpisode"
        return verifiedServersMap[key]?.toList() ?: emptyList()
    }

    fun getVerifiedServerStream(tmdbId: String, season: Int = 1, episode: Int = 1, serverKey: String): ScrapedStreamResult? {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val directKey = "$tmdbId-$season-$effectiveEpisode-$serverKey"
        return verifiedServerDirectCache[directKey]
    }

    suspend fun deepScrapeAndVerifyAllServers(
        context: Context,
        tmdbId: String,
        title: String,
        isTv: Boolean,
        season: Int,
        episode: Int,
        isAnime: Boolean = false,
        onServerFound: ((VerifiedStreamServer) -> Unit)? = null
    ): List<VerifiedStreamServer> = withContext(Dispatchers.IO) {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val cacheKey = "$tmdbId-$season-$effectiveEpisode"

        val existing = verifiedServersMap[cacheKey]
        if (existing != null && existing.isNotEmpty()) {
            existing.forEach { onServerFound?.invoke(it) }
            return@withContext existing.toList()
        }

        val serverList = java.util.concurrent.CopyOnWriteArrayList<VerifiedStreamServer>()
        verifiedServersMap[cacheKey] = serverList

        // 1. Resolve TMDB ID and TV flag
        var effectiveIsTv = isTv || tmdbId.startsWith("series_") || (!tmdbId.startsWith("movie_") && (title.contains("Season", true) || season > 1))
        var finalTmdbId = VidnestNativeScraper.resolveToNumericTmdbId(tmdbId, effectiveIsTv)
        if (finalTmdbId.startsWith("movie_")) {
            finalTmdbId = finalTmdbId.removePrefix("movie_")
            effectiveIsTv = false
        } else if (finalTmdbId.startsWith("series_")) {
            finalTmdbId = finalTmdbId.removePrefix("series_")
            effectiveIsTv = true
        }

        if (!finalTmdbId.all { it.isDigit() }) {
            if (finalTmdbId.startsWith("tt")) {
                try {
                    val findRes = com.example.data.network.RetrofitClient.tmdbApi.getByExternalId(finalTmdbId, "imdb_id")
                    val tv = findRes.tv_results?.firstOrNull()
                    val movie = findRes.movie_results?.firstOrNull()
                    if (tv != null && effectiveIsTv) {
                        finalTmdbId = tv.id.toString()
                    } else if (movie != null && !effectiveIsTv) {
                        finalTmdbId = movie.id.toString()
                    } else if (tv != null) {
                        finalTmdbId = tv.id.toString()
                        effectiveIsTv = true
                    } else if (movie != null) {
                        finalTmdbId = movie.id.toString()
                    }
                } catch (_: Exception) {}
            }
        }

        if (!finalTmdbId.all { it.isDigit() }) {
            try {
                val cleanQ = sanitizeTitle(title)
                val q = if (cleanQ.isNotBlank()) cleanQ else title.replace(Regex("""(?i)(?:season|part|cour|arc|s)\s*\d+.*"""), "").trim()
                val sr = if (effectiveIsTv) {
                    com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = q).results?.firstOrNull()
                } else {
                    com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = q).results?.firstOrNull()
                }
                if (sr != null) {
                    finalTmdbId = sr.id.toString()
                }
            } catch (_: Exception) {}
        }

        // Helper to register verified working server
        suspend fun registerVerified(key: String, name: String, type: String, colorHex: Long, res: ScrapedStreamResult) {
            if (res.streamUrl.isBlank() || blacklistedUrls.contains(res.streamUrl)) return
            val item = VerifiedStreamServer(key, name, type, colorHex, res)
            if (serverList.none { it.key == key }) {
                serverList.add(item)
                verifiedServerDirectCache["$cacheKey-$key"] = res
                if (streamCache[cacheKey] == null || streamCache[cacheKey]?.result?.streamUrl.isNullOrBlank()) {
                    streamCache[cacheKey] = TimestampedStream(res)
                    saveToRoomCache(context, cacheKey, res)
                }
                onServerFound?.invoke(item)
            }
        }

        // Deep parallel fleet running across all servers simultaneously
        coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Job>()

            fun addScraper(key: String, name: String, type: String, colorHex: Long, scrapeBlock: suspend () -> ScrapedStreamResult?) {
                jobs.add(launch(Dispatchers.IO) {
                    try {
                        val res = scrapeBlock()
                        if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                            registerVerified(key, name, type, colorHex, res)
                        }
                    } catch (_: Exception) {}
                })
            }

            // Priority 1: Beta (Vidxyz)
            addScraper("beta", "Beta", "DIRECT", 0xFF00E5FF) {
                VidnestNativeScraper.extractStreamFromProvider("beta", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 2: Sigma (HollyMovieHD)
            addScraper("sigma", "Sigma", "DIRECT", 0xFF3F51B5) {
                VidnestNativeScraper.extractStreamFromProvider("sigma", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 3: VidLink Direct
            addScraper("vidlink_direct", "VidLink", "DIRECT", 0xFF6C5CE7) {
                VidLinkNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 4: ZOZO Direct (Vidrock)
            addScraper("vidrock_direct", "ZOZO", "DIRECT", 0xFF00E5FF) {
                VidrockNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 5: Prime
            addScraper("prime", "Prime", "DIRECT", 0xFF00E676) {
                VidnestNativeScraper.extractStreamFromProvider("prime", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 6: Hexa Prime
            addScraper("hexa", "Hexa Prime", "DIRECT", 0xFF009688) {
                VidnestNativeScraper.extractStreamFromProvider("hexa", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 7: Gama (Vidzee)
            addScraper("gama", "Gamma", "DIRECT", 0xFFFF5722) {
                VidnestNativeScraper.extractStreamFromProvider("gama", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 8: Alfa (Videasy)
            addScraper("alfa", "Alfa", "DIRECT", 0xFF4CAF50) {
                VidnestNativeScraper.extractStreamFromProvider("alfa", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 9: VidSrc Multi-Host
            addScraper("vidsrc_direct", "VidSrc", "MULTI", 0xFFFF5252) {
                VidSrcNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 10: AutoEmbed Direct
            addScraper("autoembed_direct", "AutoEmbed", "DIRECT", 0xFF00B894) {
                AutoEmbedNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 11: Ophim
            addScraper("ophim", "Ophim", "DIRECT", 0xFF00B0FF) {
                VidnestNativeScraper.extractStreamFromProvider("ophim", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 12: Catflix
            addScraper("catflix", "Catflix", "DIRECT", 0xFFFF4081) {
                VidnestNativeScraper.extractStreamFromProvider("catflix", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Priority 13: Zeta
            addScraper("zeta", "Zeta", "DIRECT", 0xFF9C27B0) {
                VidnestNativeScraper.extractStreamFromProvider("zeta", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
            }

            // Movie-only routes
            if (!effectiveIsTv) {
                addScraper("filxer", "HM VIP", "DIRECT", 0xFFE91E63) {
                    VidnestNativeScraper.extractStreamFromProvider("filxer", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                }
                addScraper("delta", "Hindi", "DIRECT", 0xFFFF9800) {
                    VidnestNativeScraper.extractStreamFromProvider("delta", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                }
                addScraper("lamda", "Lamda", "DIRECT", 0xFF7C4DFF) {
                    VidnestNativeScraper.extractStreamFromProvider("lamda", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                }
            }

            // Anime routes (Anikoto Sub/Dub)
            if (isAnime) {
                jobs.add(launch(Dispatchers.IO) {
                    try {
                        val group = AnikotoScraper.fetchAvailableServers(
                            title = title,
                            season = season,
                            episode = effectiveEpisode
                        )
                        val watchUrl = group.watchUrl.ifEmpty { "https://anikoto.cz" }
                        group.subServers.forEach { srv ->
                            launch(Dispatchers.IO) {
                                try {
                                    val res = AnikotoScraper.extractStreamFromServer(srv, watchUrl, effectiveEpisode)
                                    if (res != null && res.streamUrl.isNotBlank()) {
                                        registerVerified("anikoto_sub_${srv.linkId}", "SUB: ${srv.name}", "SUB", 0xFF00E5FF, res)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        group.dubServers.forEach { srv ->
                            launch(Dispatchers.IO) {
                                try {
                                    val res = AnikotoScraper.extractStreamFromServer(srv, watchUrl, effectiveEpisode)
                                    if (res != null && res.streamUrl.isNotBlank()) {
                                        registerVerified("anikoto_dub_${srv.linkId}", "DUB: ${srv.name}", "DUB", 0xFFB388FF, res)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                })
            }

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 6000L) {
                if (jobs.all { it.isCompleted }) break
                kotlinx.coroutines.delay(80L)
            }
        }

        serverList.toList()
    }

    suspend fun raceFastestServerStream(
        context: Context,
        tmdbId: String,
        title: String,
        isTv: Boolean,
        season: Int,
        episode: Int,
        preferredServerKey: String? = null
    ): StreamRaceWinner? = withContext(Dispatchers.IO) {
        val effectiveEpisode = if (episode <= 0) 1 else episode
        val cacheKey = "$tmdbId-$season-$effectiveEpisode"

        // 1. Check in-memory cache if no specific server is forced
        if (preferredServerKey == null || preferredServerKey == "fastest_auto") {
            val memCached = streamCache[cacheKey]
            if (memCached != null && System.currentTimeMillis() - memCached.timestamp < 15 * 60 * 1000L && !blacklistedUrls.contains(memCached.result.streamUrl)) {
                if (memCached.result.streamUrl.isNotBlank()) {
                    return@withContext StreamRaceWinner("fastest_auto", "Fastest Direct", memCached.result)
                }
            }
        }

        // 2. Resolve numeric TMDB ID & clean title once upfront
        var effectiveIsTv = isTv || tmdbId.startsWith("series_") || (!tmdbId.startsWith("movie_") && (title.contains("Season", true) || season > 1))
        var finalTmdbId = VidnestNativeScraper.resolveToNumericTmdbId(tmdbId, effectiveIsTv)
        if (finalTmdbId.startsWith("movie_")) {
            finalTmdbId = finalTmdbId.removePrefix("movie_")
            effectiveIsTv = false
        } else if (finalTmdbId.startsWith("series_")) {
            finalTmdbId = finalTmdbId.removePrefix("series_")
            effectiveIsTv = true
        }

        if (!finalTmdbId.all { it.isDigit() }) {
            if (finalTmdbId.startsWith("tt")) {
                try {
                    val findRes = com.example.data.network.RetrofitClient.tmdbApi.getByExternalId(finalTmdbId, "imdb_id")
                    val tv = findRes.tv_results?.firstOrNull()
                    val movie = findRes.movie_results?.firstOrNull()
                    if (tv != null && effectiveIsTv) {
                        finalTmdbId = tv.id.toString()
                    } else if (movie != null && !effectiveIsTv) {
                        finalTmdbId = movie.id.toString()
                    } else if (tv != null) {
                        finalTmdbId = tv.id.toString()
                        effectiveIsTv = true
                    } else if (movie != null) {
                        finalTmdbId = movie.id.toString()
                    }
                } catch (_: Exception) {}
            }
        }

        if (!finalTmdbId.all { it.isDigit() }) {
            try {
                val cleanQ = sanitizeTitle(title)
                val q = if (cleanQ.isNotBlank()) cleanQ else title.replace(Regex("""(?i)(?:season|part|cour|arc|s)\s*\d+.*"""), "").trim()
                val sr = if (effectiveIsTv) {
                    com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = q).results?.firstOrNull()
                } else {
                    com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = q).results?.firstOrNull()
                }
                if (sr != null) {
                    finalTmdbId = sr.id.toString()
                } else {
                    val altSr = if (effectiveIsTv) {
                        com.example.data.network.RetrofitClient.tmdbApi.searchMovies(query = q).results?.firstOrNull()?.also { effectiveIsTv = false }
                    } else {
                        com.example.data.network.RetrofitClient.tmdbApi.searchTvShows(query = q).results?.firstOrNull()?.also { effectiveIsTv = true }
                    }
                    if (altSr != null) {
                        finalTmdbId = altSr.id.toString()
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. If user selected a specific provider (e.g., Hindi, VidRock, Flixer, Prime, Hexa)
        if (!preferredServerKey.isNullOrBlank() && preferredServerKey != "fastest_auto") {
            try {
                val specificResult: ScrapedStreamResult? = when (preferredServerKey) {
                    "vidrock_direct" -> VidrockNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    "vidlink_direct" -> VidLinkNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    "autoembed_direct" -> AutoEmbedNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    "vidsrc_direct" -> VidSrcNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    else -> VidnestNativeScraper.extractStreamFromProvider(preferredServerKey, finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                }
                if (specificResult != null && specificResult.streamUrl.isNotBlank()) {
                    val serverName = when (preferredServerKey) {
                        "delta" -> "HINDI"
                        "vidrock_direct" -> "ZOZO (Direct)"
                        "filxer" -> "HM VIP"
                        "prime" -> "Prime"
                        "hexa" -> "Hexa"
                        "alfa" -> "Alfa"
                        "gama" -> "Gama"
                        "zeta" -> "Zeta"
                        "lamda" -> "Lamda"
                        "catflix" -> "Catflix"
                        "beta" -> "Beta"
                        "sigma" -> "Sigma"
                        "ophim" -> "Ophim"
                        "vidsrc_direct" -> "VidSrc (Multi)"
                        else -> preferredServerKey.uppercase()
                    }
                    streamCache[cacheKey] = TimestampedStream(specificResult)
                    saveToRoomCache(context, cacheKey, specificResult)
                    return@withContext StreamRaceWinner(preferredServerKey, serverName, specificResult)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Preferred server $preferredServerKey failed: ${e.message}")
            }
        }

        // 4. PARALLEL SPEED RACE ACROSS MULTI LIST OF SERVERS:
        // Priority 1 (Default): Vidnest.fun Sub-Server Parallel Fleet
        // [Beta, Sigma, Gama, Alfa, Hexa, Prime, Zeta, Catflix, Ophim, + Delta/Lamda/Flixer for Movies]
        val vidnestWinner = coroutineScope {
            val vidnestChannel = kotlinx.coroutines.channels.Channel<StreamRaceWinner>(15)

            // Priority Server 1: Beta (Vidxyz) - Most reliable for both Movies and TV series
            val jBeta = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("beta", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("beta", "Beta", res))
                    }
                } catch (_: Exception) {}
            }

            // Priority Server 2: Sigma (HollyMovieHD)
            val jSigma = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("sigma", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("sigma", "Sigma", res))
                    }
                } catch (_: Exception) {}
            }

            // Priority Server 3: Gama (Vidzee)
            val jGama = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("gama", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("gama", "Gama", res))
                    }
                } catch (_: Exception) {}
            }

            // Priority Server 4: Alfa (Videasy)
            val jAlfa = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("alfa", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("alfa", "Alfa", res))
                    }
                } catch (_: Exception) {}
            }

            // Priority Server 5: Hexa (Vidlink)
            val jHexa = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("hexa", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("hexa", "Hexa", res))
                    }
                } catch (_: Exception) {}
            }

            // Priority Server 6: Prime (Vidrock)
            val jPrime = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("prime", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("prime", "Prime", res))
                    }
                } catch (_: Exception) {}
            }

            val jFlixer = if (!effectiveIsTv) {
                launch(Dispatchers.IO) {
                    try {
                        val res = VidnestNativeScraper.extractStreamFromProvider("filxer", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                        if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                            vidnestChannel.trySend(StreamRaceWinner("filxer", "HM VIP", res))
                        }
                    } catch (_: Exception) {}
                }
            } else null

            val jHindi = if (!effectiveIsTv) {
                launch(Dispatchers.IO) {
                    try {
                        val res = VidnestNativeScraper.extractStreamFromProvider("delta", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                        if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                            vidnestChannel.trySend(StreamRaceWinner("delta", "HINDI", res))
                        }
                    } catch (_: Exception) {}
                }
            } else null

            val jLamda = if (!effectiveIsTv) {
                launch(Dispatchers.IO) {
                    try {
                        val res = VidnestNativeScraper.extractStreamFromProvider("lamda", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                        if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                            vidnestChannel.trySend(StreamRaceWinner("lamda", "Lamda", res))
                        }
                    } catch (_: Exception) {}
                }
            } else null

            val jOphim = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("ophim", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("ophim", "Ophim", res))
                    }
                } catch (_: Exception) {}
            }

            val jZeta = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("zeta", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("zeta", "Zeta", res))
                    }
                } catch (_: Exception) {}
            }

            val jCatflix = launch(Dispatchers.IO) {
                try {
                    val res = VidnestNativeScraper.extractStreamFromProvider("catflix", finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        vidnestChannel.trySend(StreamRaceWinner("catflix", "Catflix", res))
                    }
                } catch (_: Exception) {}
            }

            val vidnestJobs = listOfNotNull(jBeta, jSigma, jGama, jAlfa, jHexa, jPrime, jFlixer, jHindi, jLamda, jOphim, jZeta, jCatflix)
            var winner: StreamRaceWinner? = null
            val startTime = System.currentTimeMillis()

            try {
                while (System.currentTimeMillis() - startTime < 4500L) {
                    val candidate = kotlinx.coroutines.withTimeoutOrNull(50L) {
                        vidnestChannel.receive()
                    }
                    if (candidate != null && candidate.result.streamUrl.isNotBlank()) {
                        winner = candidate
                        break
                    }
                    if (vidnestJobs.all { it.isCompleted }) break
                }
            } catch (_: Exception) {}

            vidnestJobs.forEach { it.cancel() }
            winner
        }

        if (vidnestWinner != null && vidnestWinner.result.streamUrl.isNotBlank()) {
            Log.d(TAG, "Fastest Vidnest.fun Sub-Server Winner: [${vidnestWinner.serverName}] -> ${vidnestWinner.result.streamUrl}")
            streamCache[cacheKey] = TimestampedStream(vidnestWinner.result)
            saveToRoomCache(context, cacheKey, vidnestWinner.result)
            return@withContext vidnestWinner
        }

        // Priority 2 (Fallback): Secondary Deep Scraping Tier (VidRock, VidSrc, VidLink, AutoEmbed)
        Log.d(TAG, "Vidnest sub-servers not found. Cascading to Secondary Deep Scrapers (VidRock, VidSrc, VidLink, AutoEmbed)...")
        coroutineScope {
            val fallbackChannel = kotlinx.coroutines.channels.Channel<StreamRaceWinner>(8)

            // Server A: VidRock Direct
            val jVidrock = launch(Dispatchers.IO) {
                try {
                    val res = VidrockNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        fallbackChannel.trySend(StreamRaceWinner("vidrock_direct", "ZOZO (Direct)", res))
                    }
                } catch (_: Exception) {}
            }

            // Server B: VidSrc Multi-Host
            val jVidsrc = launch(Dispatchers.IO) {
                try {
                    val res = VidSrcNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        fallbackChannel.trySend(StreamRaceWinner("vidsrc_direct", "VidSrc", res))
                    }
                } catch (_: Exception) {}
            }

            // Server C: VidLink Direct
            val jVidlink = launch(Dispatchers.IO) {
                try {
                    val res = VidLinkNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        fallbackChannel.trySend(StreamRaceWinner("vidlink_direct", "VidLink", res))
                    }
                } catch (_: Exception) {}
            }

            // Server D: AutoEmbed Direct
            val jAutoEmbed = launch(Dispatchers.IO) {
                try {
                    val res = AutoEmbedNativeScraper.extractStream(finalTmdbId, effectiveIsTv, season, effectiveEpisode)
                    if (res != null && res.streamUrl.isNotBlank() && verifyStreamAlive(res.streamUrl, res.headers)) {
                        fallbackChannel.trySend(StreamRaceWinner("autoembed_direct", "AutoEmbed", res))
                    }
                } catch (_: Exception) {}
            }

            val fallbackJobs = listOf(jVidrock, jVidsrc, jVidlink, jAutoEmbed)
            var fallbackWinner: StreamRaceWinner? = null
            val startTime = System.currentTimeMillis()

            try {
                while (System.currentTimeMillis() - startTime < 8000L) {
                    val candidate = kotlinx.coroutines.withTimeoutOrNull(50L) {
                        fallbackChannel.receive()
                    }
                    if (candidate != null && candidate.result.streamUrl.isNotBlank()) {
                        fallbackWinner = candidate
                        break
                    }
                    if (fallbackJobs.all { it.isCompleted }) break
                }
            } catch (_: Exception) {}

            fallbackJobs.forEach { it.cancel() }

            if (fallbackWinner != null && fallbackWinner.result.streamUrl.isNotBlank()) {
                Log.d(TAG, "Secondary Deep Scraping Winner: [${fallbackWinner.serverName}] -> ${fallbackWinner.result.streamUrl}")
                streamCache[cacheKey] = TimestampedStream(fallbackWinner.result)
                saveToRoomCache(context, cacheKey, fallbackWinner.result)
                return@coroutineScope fallbackWinner
            }

            null
        }
    }
}
