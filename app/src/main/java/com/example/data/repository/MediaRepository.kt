package com.example.data.repository

import com.example.data.model.MediaItem
import com.example.data.network.RetrofitClient
import com.example.data.network.TmdbMediaResult
import com.example.data.network.TmdbMediaDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MediaRepository {
    
    private val tmdbApi = RetrofitClient.tmdbApi
    private val youtubeApi = RetrofitClient.youtubeApi
    private val detailCache = ConcurrentHashMap<String, TmdbMediaDetail>()

    suspend fun searchYouTube(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = com.example.BuildConfig.YOUTUBE_API_KEY
            if (apiKey.isNotEmpty() && apiKey != "MY_YOUTUBE_API_KEY_DEFAULT_VALUE") {
                val response = youtubeApi.searchVideos(query = query, apiKey = apiKey)
                val id = response.items.firstOrNull()?.id?.videoId
                if (!id.isNullOrEmpty()) return@withContext id
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Reliable fallback: Scrape YouTube search results HTML without API key
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val regexes = listOf(
                        """"videoRenderer":\{"videoId":"([a-zA-Z0-9_-]{11})"""".toRegex(),
                        """"videoId":"([a-zA-Z0-9_-]{11})"""".toRegex(),
                        """/watch\?v=([a-zA-Z0-9_-]{11})""".toRegex()
                    )
                    for (regex in regexes) {
                        val match = regex.find(html)
                        if (match != null) {
                            val id = match.groupValues[1]
                            if (id.isNotEmpty() && id != "null") {
                                return@withContext id
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Secondary Fallback: DuckDuckGo search query for YouTube video ID
        try {
            val ddgUrl = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode("site:youtube.com $query", "UTF-8")}"
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val ddgMatch = """v=([a-zA-Z0-9_-]{11})""".toRegex().find(html)
                    if (ddgMatch != null) {
                        return@withContext ddgMatch.groupValues[1]
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        null
    }

    private val defaultCinemaPosters = listOf(
        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80",
        "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&q=80",
        "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500&q=80",
        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
        "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
        "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?w=500&q=80",
        "https://images.unsplash.com/photo-1535016120720-40c646be5580?w=500&q=80",
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&q=80",
        "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500&q=80"
    )

    private fun isTmdbAnime(result: TmdbMediaResult): Boolean {
        // TMDB Genre 16 is Animation. If genre_ids contains 16 or original language is Japanese ("ja"), treat as anime
        if (result.genre_ids?.contains(16) == true) return true
        if (result.original_language?.equals("ja", ignoreCase = true) == true) return true
        val titleLower = (result.title ?: result.name ?: "").lowercase()
        if (titleLower.contains("anime") || titleLower.contains("naruto") || titleLower.contains("one piece") || titleLower.contains("dragon ball") || titleLower.contains("jujutsu kaisen") || titleLower.contains("demon slayer") || titleLower.contains("attack on titan") || titleLower.contains("bleach")) {
            return true
        }
        return false
    }

    private fun mapToMediaItem(result: TmdbMediaResult, category: String, type: String = "auto"): MediaItem {
        val displayTitle = if (result.name != null && result.title == null) result.name else (result.title ?: result.name ?: "Unknown")
        
        // Authoritative TMDB Media Type resolution:
        val resolvedType = when {
            result.media_type.equals("movie", ignoreCase = true) -> "movie"
            result.media_type.equals("tv", ignoreCase = true) -> "series"
            type == "movie" -> "movie"
            type == "series" || type == "tv" -> "series"
            result.name != null && result.title == null -> "series"
            result.title != null && result.name == null -> "movie"
            result.first_air_date != null && result.release_date == null -> "series"
            result.release_date != null && result.first_air_date == null -> "movie"
            category.contains("Series", ignoreCase = true) || category.contains("TV Shows", ignoreCase = true) || category.contains("Natok", ignoreCase = true) -> "series"
            category.contains("Movie", ignoreCase = true) || category.contains("Cinema", ignoreCase = true) -> "movie"
            else -> "movie"
        }

        val year = (result.release_date ?: result.first_air_date ?: "").take(4)
        val posterPath = result.poster_path ?: result.backdrop_path ?: ""
        val imageUrl = if (posterPath.isNotEmpty()) {
            "https://image.tmdb.org/t/p/w500$posterPath"
        } else {
            val idx = kotlin.math.abs(displayTitle.hashCode()) % defaultCinemaPosters.size
            defaultCinemaPosters[idx]
        }
        val tmdbId = result.id.toString()
        val streamUrl = if (resolvedType == "movie") "https://vidsrc.to/embed/movie/$tmdbId" else "https://vidsrc.to/embed/tv/$tmdbId/1/1"
        
        return MediaItem(
            id = "${resolvedType}_$tmdbId",
            title = displayTitle,
            category = category,
            imageUrl = imageUrl,
            rating = String.format("%.1f", result.vote_average ?: 0.0),
            year = year,
            description = result.overview ?: "",
            streamUrl = streamUrl,
            episodes = if (resolvedType == "series") "Season 1" else "",
            isStreamable = true,
            imdbId = tmdbId, // Using TMDB ID as imdbId since vidsrc/vidnest supports tmdb id directly
            type = resolvedType
        )
    }

    suspend fun detectOrVerifyMediaTypeWithAI(title: String, year: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val prompt = "Is the title '$title' ${if (year.isNotBlank()) "($year)" else ""} a 'movie' or a 'series'? Reply ONLY with one word: 'movie' or 'series'."
            val request = com.example.data.network.GeminiRequest(
                contents = listOf(
                    com.example.data.network.GeminiContent(
                        parts = listOf(com.example.data.network.GeminiPart(text = prompt))
                    )
                )
            )
            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) return@withContext "movie"
            val response = com.example.data.network.GeminiClient.apiService.generateContent(
                model = "gemini-2.5-flash",
                apiKey = apiKey,
                request = request
            )
            val candidateText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()?.lowercase() ?: ""
            if (candidateText.contains("series") || candidateText.contains("tv")) {
                return@withContext "series"
            } else if (candidateText.contains("movie")) {
                return@withContext "movie"
            }
        } catch (_: Exception) {}
        "movie"
    }

    fun getCuratedLatestItems(): List<MediaItem> {
        return emptyList()
    }

    suspend fun fetchLatestReleases(page: Int = 1): List<MediaItem> = coroutineScope {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MediaItem>>>()

        fun addJob(category: String, type: String, apiCall: suspend (Int) -> com.example.data.network.TmdbResponse) {
            jobs.add(async(Dispatchers.IO) {
                try {
                    val res = apiCall(page).results ?: emptyList()
                    res.filter { !isTmdbAnime(it) }.map { mapToMediaItem(it, category, type) }
                } catch (e: Exception) {
                    emptyList()
                }
            })
        }

        // Fetch now playing movies, latest released movies, on-the-air TV shows (excluding anime from TMDB)
        addJob("Latest", "movie") { tmdbApi.getNowPlayingMovies(page = it) }
        addJob("Latest", "movie") { tmdbApi.getLatestReleasedMovies(page = it) }
        addJob("Latest", "series") { tmdbApi.getOnTheAirTvShows(page = it) }
        // All anime in Latest is sourced from Anikoto + TMDB Anime
        jobs.add(async(Dispatchers.IO) {
            try {
                val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(sortBy = "latest-updated", page = page)
                list.map { item ->
                    val isMovie = item.type.lowercase().contains("movie")
                    val type = if (isMovie) "movie" else "series"
                    val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = if (isMovie) "Latest Anime Movies" else "Latest Anime Series",
                        imageUrl = poster.ifBlank { if (isMovie) "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80" else "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80" },
                        rating = if (item.rating.isNotBlank()) item.rating else "8.4",
                        year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2025",
                        description = item.description,
                        streamUrl = item.watchUrl,
                        episodes = item.episodesInfo,
                        isStreamable = true,
                        imdbId = "anikoto_${item.id}",
                        type = type
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        })
        jobs.add(async(Dispatchers.IO) {
            try {
                val res = tmdbApi.getLatestAiringAnime(page = page).results ?: emptyList()
                res.map { mapToMediaItem(it, "Latest Anime Series", "series") }
            } catch (e: Exception) {
                emptyList()
            }
        })
        addJob("Latest", "movie") { tmdbApi.getTrendingToday(page = it) }

        val results = jobs.awaitAll().flatten().filter { it.imageUrl.isNotEmpty() }.distinctBy { it.id }
        val enhanced = com.example.scraper.AnimePosterEngine.enhanceMediaItems(results)
        enhanced.sortedWith(
            compareByDescending<MediaItem> { it.year.toIntOrNull() ?: 0 }
                .thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
        )
    }

    suspend fun fetchMediaItems(): List<MediaItem> = coroutineScope {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MediaItem>>>()

        fun addJob(category: String, type: String, apiCall: suspend (Int) -> com.example.data.network.TmdbResponse) {
            jobs.add(async(Dispatchers.IO) {
                try {
                    val res = apiCall(1).results ?: emptyList()
                    res.filter { !isTmdbAnime(it) }.map { mapToMediaItem(it, category, type) }
                } catch (e: Exception) {
                    emptyList()
                }
            })
        }

        // Initially load latest releases (which includes latest Anikoto anime) & core non-anime categories from TMDB
        jobs.add(async(Dispatchers.IO) { fetchLatestReleases(1) })
        addJob("Movies", "movie") { tmdbApi.getTrendingMovies(page = it) }
        addJob("Series & TV Shows", "series") { tmdbApi.getTrendingTvShows(page = it) }

        // Fetch direct Anikoto Anime Series & Movies for "All" tab and "Anime" tabs
        jobs.add(async(Dispatchers.IO) {
            try {
                val animeSeries = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "TV", sortBy = "latest-updated", page = 1)
                animeSeries.map { item ->
                    val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Series",
                        imageUrl = poster.ifBlank { "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80" },
                        rating = if (item.rating.isNotBlank()) item.rating else "8.3",
                        year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2024",
                        description = item.description,
                        streamUrl = item.watchUrl,
                        episodes = item.episodesInfo,
                        isStreamable = true,
                        imdbId = "anikoto_${item.id}",
                        type = "series"
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        })
        jobs.add(async(Dispatchers.IO) {
            try {
                val res = tmdbApi.getAnime(page = 1).results ?: emptyList()
                res.map { mapToMediaItem(it, "Anime Series", "series") }
            } catch (e: Exception) {
                emptyList()
            }
        })
        jobs.add(async(Dispatchers.IO) {
            try {
                val animeMovies = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "Movie", sortBy = "latest-updated", page = 1)
                animeMovies.map { item ->
                    val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Movies",
                        imageUrl = poster.ifBlank { "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80" },
                        rating = if (item.rating.isNotBlank()) item.rating else "8.5",
                        year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2024",
                        description = item.description,
                        streamUrl = item.watchUrl,
                        episodes = "",
                        isStreamable = true,
                        imdbId = "anikoto_${item.id}",
                        type = "movie"
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        })
        jobs.add(async(Dispatchers.IO) {
            try {
                val res = tmdbApi.getAnimeMovies(page = 1).results ?: emptyList()
                res.map { mapToMediaItem(it, "Anime Movies", "movie") }
            } catch (e: Exception) {
                emptyList()
            }
        })

        val fetchedList = jobs.awaitAll().flatten().toMutableList()

            // Top Curated Popular Bangla OTT Web Series, Natok & Movies with exact TMDB IDs & TMDB posters
            val curatedBanglaItems = listOf(
                MediaItem(
                    id = "movie_1216385",
                    title = "Toofan ( তুফান )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/5TS3piPf3ZqISbRSrJ8lYeGQvfQ.jpg",
                    rating = "8.6",
                    year = "2024",
                    description = "An infamous 1990s Bangladeshi gangster rises to power, ruling the underworld with high-octane action.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/1216385",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "1216385",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_961707",
                    title = "Hawa ( হাওয়া )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/pwtd4TPJXi26cnEgKf3pczx4kXZ.jpg",
                    rating = "8.4",
                    year = "2022",
                    description = "A group of fishermen in the Bay of Bengal catch a mysterious girl in their net, triggering paranoia and supernatural occurrences.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/961707",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "961707",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_1089729",
                    title = "Devi Chowdhurani ( দেবী চৌধুরানী )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/wJ1k4A31BSV7e4lyalpnWaVGusI.jpg",
                    rating = "9.0",
                    year = "2025",
                    description = "The epic period action-adventure film based on Bankim Chandra Chatterjee's masterpiece, depicting Profulla's rise as a legendary outlaw queen.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/1089729",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "1089729",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_1142273",
                    title = "Priyotoma ( প্রিয়তমা )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/o6gX547Q7p2x4PqIcxw27tq2s1j.jpg",
                    rating = "8.2",
                    year = "2023",
                    description = "An emotional romantic action drama depicting a lover's journey through time, sacrifices, and destiny.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/1142273",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "1142273",
                    type = "movie"
                ),
                MediaItem(
                    id = "series_207305",
                    title = "Karagar ( কারাগার )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/8tLoJ31NLwhlP5PjJItBCptRV4t.jpg",
                    rating = "8.8",
                    year = "2022",
                    description = "A mysterious prisoner appears inside Cell No. 145 of Akashgar High Security Jail, claiming to have been imprisoned for over 250 years.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/207305/1/1",
                    episodes = "Season 1 & 2",
                    isStreamable = true,
                    imdbId = "207305",
                    type = "series"
                ),
                MediaItem(
                    id = "series_128073",
                    title = "Mohanagar ( মহানগর )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/bhmI8l62JIc0AuURN4ZL7i6t82K.jpg",
                    rating = "8.9",
                    year = "2021",
                    description = "Inside a Dhaka police station, a fateful night unfolds involving an influential politician's son, an honest officer, and a corrupt system.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/128073/1/1",
                    episodes = "Season 1 & 2",
                    isStreamable = true,
                    imdbId = "128073",
                    type = "series"
                ),
                MediaItem(
                    id = "series_114809",
                    title = "Taqdeer ( তাকদীর )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/nXgDr8WmsTRkzHvAKqYuLN991lS.jpg",
                    rating = "8.7",
                    year = "2020",
                    description = "A freezer van driver finds an unidentified corpse inside his vehicle, unraveling a high-stakes conspiracy.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/114809/1/1",
                    episodes = "Season 1",
                    isStreamable = true,
                    imdbId = "114809",
                    type = "series"
                ),
                MediaItem(
                    id = "series_205471",
                    title = "Kaiser ( কায়জার )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/7rWw4a6f2vG6t3E94K4rZ39bA9b.jpg",
                    rating = "8.5",
                    year = "2022",
                    description = "Kaiser Chowdhury, a troubled homicide detective with exceptional deducing abilities, uncovers dark secrets in Dhaka.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/205471/1/1",
                    episodes = "Season 1",
                    isStreamable = true,
                    imdbId = "205471",
                    type = "series"
                ),
                MediaItem(
                    id = "series_205831",
                    title = "Syndicate ( সিন্ডিকেট )",
                    category = "Bangla",
                    imageUrl = "https://image.tmdb.org/t/p/w500/3q6eE9x0b5zP6mK2f7g7VwP5j01.jpg",
                    rating = "8.4",
                    year = "2022",
                    description = "An autistic bank officer attempts to solve the mysterious suicide of his colleague, entering a dangerous web of deception.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/205831/1/1",
                    episodes = "Season 1",
                    isStreamable = true,
                    imdbId = "205831",
                    type = "series"
                )
            )
            fetchedList.addAll(curatedBanglaItems)
            fetchedList.retainAll { it.imageUrl.isNotEmpty() }
            val distinct = fetchedList.distinctBy { it.id }
            com.example.scraper.AnimePosterEngine.enhanceMediaItems(distinct)
        }

    suspend fun fetchCastMembers(id: String, type: String): List<com.example.ui.components.CastMember> = withContext(Dispatchers.IO) {
        try {
            val tmdbType = if (type == "series" || type == "tv") "tv" else "movie"
            var finalTmdbId = if (id.startsWith("movie_") || id.startsWith("series_")) {
                id.split("_").getOrNull(1) ?: id
            } else {
                id
            }

            if (finalTmdbId.startsWith("tt")) {
                val findResponse = tmdbApi.getByExternalId(finalTmdbId)
                finalTmdbId = if (tmdbType == "movie") {
                    findResponse.movie_results?.firstOrNull()?.id?.toString() ?: ""
                } else {
                    findResponse.tv_results?.firstOrNull()?.id?.toString() ?: ""
                }
            }
            
            if (finalTmdbId.isEmpty()) return@withContext emptyList()
            
            val response = tmdbApi.getCredits(type = tmdbType, id = finalTmdbId)
            val castArray = response.cast ?: emptyList()
            castArray.take(15).map { cast ->
                com.example.ui.components.CastMember(
                    name = cast.name ?: "Unknown",
                    character = cast.character ?: "",
                    profilePath = cast.profile_path
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchMediaDetails(id: String, type: String): com.example.data.network.TmdbMediaDetail = withContext(Dispatchers.IO) {
        var tmdbType = if (type == "series" || type == "tv") "tv" else "movie"
        var finalId = id
        if (finalId.startsWith("movie_") || finalId.startsWith("series_") || finalId.startsWith("anikoto_")) {
            finalId = finalId.substringAfter("_")
        }
        detailCache[finalId]?.let { return@withContext it }

        if (finalId.startsWith("tt")) {
            try {
                val findResponse = tmdbApi.getByExternalId(finalId)
                val movieMatch = findResponse.movie_results?.firstOrNull()
                val tvMatch = findResponse.tv_results?.firstOrNull()

                if (tmdbType == "movie" && movieMatch != null) {
                    finalId = movieMatch.id.toString()
                } else if (tmdbType == "tv" && tvMatch != null) {
                    finalId = tvMatch.id.toString()
                } else if (movieMatch != null) {
                    finalId = movieMatch.id.toString()
                    tmdbType = "movie"
                } else if (tvMatch != null) {
                    finalId = tvMatch.id.toString()
                    tmdbType = "tv"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        detailCache[finalId]?.let { return@withContext it }

        try {
            val details = tmdbApi.getMediaDetails(type = tmdbType, id = finalId)
            detailCache[finalId] = details
            return@withContext details
        } catch (e: Exception) {
            // If primary type failed, attempt opposite type fallback (e.g. movie was actually a TV series or vice-versa)
            val alternateType = if (tmdbType == "tv") "movie" else "tv"
            try {
                val altDetails = tmdbApi.getMediaDetails(type = alternateType, id = finalId)
                detailCache[finalId] = altDetails
                return@withContext altDetails
            } catch (_: Exception) {}
            throw e
        }
    }

    suspend fun fetchCategoryItems(category: String, page: Int): List<MediaItem> = coroutineScope {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MediaItem>>>()
        
        fun addJob(catName: String, type: String, apiCall: suspend (Int) -> com.example.data.network.TmdbResponse) {
            jobs.add(async(Dispatchers.IO) {
                try {
                    val res = apiCall(page).results ?: emptyList()
                    res.filter { !isTmdbAnime(it) }.map { mapToMediaItem(it, catName, type) }
                } catch (e: Exception) {
                    emptyList()
                }
            })
        }

        val catLower = category.lowercase()
        when {
            catLower.contains("latest") || catLower.contains("new release") || catLower.contains("recent") -> {
                return@coroutineScope fetchLatestReleases(page)
            }
            catLower == "movies" -> addJob("Movies", "movie") { tmdbApi.getTrendingMovies(page = it) }
            catLower.contains("series") || catLower.contains("tv") -> addJob("Series & TV Shows", "series") { tmdbApi.getTrendingTvShows(page = it) }
            catLower.contains("anime series") || catLower == "anime" -> {
                jobs.add(async(Dispatchers.IO) {
                    try {
                        val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "TV", page = page)
                        list.map { item ->
                            val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                            MediaItem(
                                id = "anikoto_${item.id}",
                                title = item.title,
                                category = "Anime Series",
                                imageUrl = poster.ifBlank { "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80" },
                                rating = if (item.rating.isNotBlank()) item.rating else "8.2",
                                year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2024",
                                description = item.description,
                                streamUrl = item.watchUrl,
                                episodes = item.episodesInfo,
                                isStreamable = true,
                                imdbId = "anikoto_${item.id}",
                                type = "series"
                            )
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                })
                jobs.add(async(Dispatchers.IO) {
                    try {
                        val res = tmdbApi.getAnime(page = page).results ?: emptyList()
                        res.map { mapToMediaItem(it, "Anime Series", "series") }
                    } catch (e: Exception) {
                        emptyList()
                    }
                })
            }
            catLower.contains("anime movies") -> {
                jobs.add(async(Dispatchers.IO) {
                    try {
                        val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "Movie", page = page)
                        list.map { item ->
                            val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                            MediaItem(
                                id = "anikoto_${item.id}",
                                title = item.title,
                                category = "Anime Movies",
                                imageUrl = poster.ifBlank { "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80" },
                                rating = if (item.rating.isNotBlank()) item.rating else "8.5",
                                year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2024",
                                description = item.description,
                                streamUrl = item.watchUrl,
                                episodes = "",
                                isStreamable = true,
                                imdbId = "anikoto_${item.id}",
                                type = "movie"
                            )
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                })
                jobs.add(async(Dispatchers.IO) {
                    try {
                        val res = tmdbApi.getAnimeMovies(page = page).results ?: emptyList()
                        res.map { mapToMediaItem(it, "Anime Movies", "movie") }
                    } catch (e: Exception) {
                        emptyList()
                    }
                })
            }
            catLower.contains("hindi k-drama") || catLower.contains("hindi kdrama") || catLower.contains("hindi dubbed k-drama") || catLower.contains("hindi dubbed kdrama") || (catLower.contains("hindi") && catLower.contains("k-drama")) -> addJob("Hindi K-Drama", "series") { tmdbApi.getHindiDubbedKDramas(page = it) }
            catLower.contains("k-drama") || catLower.contains("kdrama") -> addJob("K-Dramas", "series") { tmdbApi.getKDramas(page = it) }
            catLower == "action" -> addJob("Action", "movie") { tmdbApi.getActionMovies(page = it) }
            catLower.contains("sci-fi") || catLower.contains("scifi") -> addJob("Sci-Fi", "movie") { tmdbApi.getSciFiMovies(page = it) }
            catLower.contains("hindi cinema") || catLower.contains("hindi movie") -> addJob("Hindi Cinema", "movie") { tmdbApi.getHindiMovies(page = it) }
            catLower.contains("hindi series") -> addJob("Hindi Series", "series") { tmdbApi.getHindiSeries(page = it) }
            catLower.contains("hindi dubbed") || catLower.contains("dubbed") -> addJob("Hindi Dubbed", "movie") { tmdbApi.getHindiDubbedMovies(page = it) }
            catLower.contains("bangla") || catLower.contains("bengali") -> {
                addJob("Bangla", "movie") { tmdbApi.getBengaliMovies(page = it) }
                addJob("Bangla", "series") { tmdbApi.getBengaliSeries(page = it) }
            }
            else -> {
                // Fallback core categories
                jobs.add(async(Dispatchers.IO) { fetchLatestReleases(page) })
                addJob("Movies", "movie") { tmdbApi.getTrendingMovies(page = it) }
                addJob("Series & TV Shows", "series") { tmdbApi.getTrendingTvShows(page = it) }
            }
        }
        val allItems = jobs.awaitAll().flatten().filter { it.imageUrl.isNotEmpty() }.distinctBy { it.id }
        if (catLower.contains("anime")) {
            com.example.scraper.AnimePosterEngine.enhanceMediaItems(allItems)
        } else {
            allItems
        }
    }

    suspend fun fetchMoreMediaItems(page: Int, category: String = "All"): List<MediaItem> = coroutineScope {
        if (category != "All") {
            return@coroutineScope fetchCategoryItems(category, page)
        }
        
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MediaItem>>>()
 
        fun addJob(category: String, type: String, apiCall: suspend (Int) -> com.example.data.network.TmdbResponse) {
            jobs.add(async(Dispatchers.IO) {
                try {
                    val res = apiCall(page).results ?: emptyList()
                    res.filter { !isTmdbAnime(it) }.map { mapToMediaItem(it, category, type) }
                } catch (e: Exception) {
                    emptyList()
                }
            })
        }
 
        // For "All", only fetch nextPage for the main core categories to remain super-fast and stable
        jobs.add(async(Dispatchers.IO) { fetchLatestReleases(page) })
        addJob("Movies", "movie") { tmdbApi.getTrendingMovies(page = it) }
        addJob("Series & TV Shows", "series") { tmdbApi.getTrendingTvShows(page = it) }
        addJob("Bangla", "movie") { tmdbApi.getBengaliMovies(page = it) }
        addJob("Bangla", "series") { tmdbApi.getBengaliSeries(page = it) }
        jobs.add(async(Dispatchers.IO) {
            try {
                val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "TV", page = page)
                list.map { item ->
                    val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Series",
                        imageUrl = poster.ifBlank { "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80" },
                        rating = if (item.rating.isNotBlank()) item.rating else "8.2",
                        year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2024",
                        description = item.description,
                        streamUrl = item.watchUrl,
                        episodes = item.episodesInfo,
                        isStreamable = true,
                        imdbId = "anikoto_${item.id}",
                        type = "series"
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        })
        jobs.add(async(Dispatchers.IO) {
            try {
                val res = tmdbApi.getAnime(page = page).results ?: emptyList()
                res.map { mapToMediaItem(it, "Anime Series", "series") }
            } catch (e: Exception) {
                emptyList()
            }
        })
        jobs.add(async(Dispatchers.IO) {
            try {
                val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "Movie", page = page)
                list.map { item ->
                    val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Movies",
                        imageUrl = poster.ifBlank { "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80" },
                        rating = if (item.rating.isNotBlank()) item.rating else "8.5",
                        year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2024",
                        description = item.description,
                        streamUrl = item.watchUrl,
                        episodes = "",
                        isStreamable = true,
                        imdbId = "anikoto_${item.id}",
                        type = "movie"
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        })
        jobs.add(async(Dispatchers.IO) {
            try {
                val res = tmdbApi.getAnimeMovies(page = page).results ?: emptyList()
                res.map { mapToMediaItem(it, "Anime Movies", "movie") }
            } catch (e: Exception) {
                emptyList()
            }
        })
 
        val fetchedList = jobs.awaitAll().flatten().filter { it.imageUrl.isNotEmpty() }.distinctBy { it.id }
        fetchedList
    }

    suspend fun searchMedia(query: String, type: String = "all"): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            // 1. Generate search variations (normalized spelling, corrected typos, and primary tokens)
            val normalizedQuery = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .replace(Regex("[^a-zA-Z0-9]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val queryWords = trimmed.split(Regex("[\\s_\\-\\.:\\(\\)\\[\\]]+")).filter { it.isNotBlank() }
            val significantTokens = queryWords.filter { it.length > 2 && !it.equals("the", true) && !it.equals("and", true) }
            val primaryToken = significantTokens.firstOrNull() ?: queryWords.firstOrNull() ?: trimmed

            // Common anime/movie typo corrections (e.g. shipudden -> shippuden / shippuuden)
            val correctedQuery = trimmed
                .replace("shipudden", "shippuden", ignoreCase = true)
                .replace("shippūden", "shippuden", ignoreCase = true)
                .replace("shippuuden", "shippuden", ignoreCase = true)
                .replace("onepeice", "one piece", ignoreCase = true)
                .replace("jujutsu kaisen", "jujutsu", ignoreCase = true)

            val searchQueriesToTry = linkedSetOf(trimmed, correctedQuery, normalizedQuery).toList()

            coroutineScope {
                // 2. Query TMDB concurrently
                val tmdbDeferred = async(Dispatchers.IO) {
                    val tmdbResults = mutableListOf<TmdbMediaResult>()
                    try {
                        for (q in searchQueriesToTry) {
                            try {
                                val list = if (type == "movie") {
                                    tmdbApi.searchMovies(query = q).results ?: emptyList()
                                } else if (type == "series" || type == "tv") {
                                    tmdbApi.searchTvShows(query = q).results ?: emptyList()
                                } else {
                                    val m = try { tmdbApi.searchMovies(query = q).results ?: emptyList() } catch (_: Exception) { emptyList() }
                                    val t = try { tmdbApi.searchTvShows(query = q).results ?: emptyList() } catch (_: Exception) { emptyList() }
                                    m + t
                                }
                                if (list.isNotEmpty()) {
                                    tmdbResults.addAll(list)
                                    break
                                }
                            } catch (_: Exception) {}
                        }

                        // If TMDB still has 0 results and we have a primary token, query TMDB with primary token
                        if (tmdbResults.isEmpty() && primaryToken != trimmed && primaryToken.length >= 3) {
                            try {
                                val list = if (type == "movie") {
                                    tmdbApi.searchMovies(query = primaryToken).results ?: emptyList()
                                } else if (type == "series" || type == "tv") {
                                    tmdbApi.searchTvShows(query = primaryToken).results ?: emptyList()
                                } else {
                                    val m = try { tmdbApi.searchMovies(query = primaryToken).results ?: emptyList() } catch (_: Exception) { emptyList() }
                                    val t = try { tmdbApi.searchTvShows(query = primaryToken).results ?: emptyList() } catch (_: Exception) { emptyList() }
                                    m + t
                                }
                                tmdbResults.addAll(list)
                            } catch (_: Exception) {}
                        }

                        tmdbResults.take(30).map { result ->
                            val requestedType = if (type == "movie" || type == "series" || type == "tv") type else "auto"
                            mapToMediaItem(result, "Search", requestedType)
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                // 3. Query Cinemeta Catalog in parallel
                val cinemetaDeferred = async(Dispatchers.IO) {
                    try {
                        val cinemetaItems = mutableListOf<MediaItem>()
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build()

                        val queryCandidates = linkedSetOf(trimmed, correctedQuery, primaryToken).filter { it.isNotBlank() }
                        for (searchCand in queryCandidates) {
                            val encoded = java.net.URLEncoder.encode(searchCand, "UTF-8")
                            val typesToFetch = when (type) {
                                "movie" -> listOf("movie")
                                "series", "tv" -> listOf("series")
                                else -> listOf("series", "movie")
                            }

                            for (cType in typesToFetch) {
                                try {
                                    val cinemetaUrl = "https://v3-cinemeta.strem.io/catalog/$cType/top/search=$encoded.json"
                                    val req = okhttp3.Request.Builder().url(cinemetaUrl).build()
                                    client.newCall(req).execute().use { resp ->
                                        if (resp.isSuccessful) {
                                            val bodyStr = resp.body?.string() ?: ""
                                            val json = org.json.JSONObject(bodyStr)
                                            val metas = json.optJSONArray("metas")
                                            if (metas != null) {
                                                for (i in 0 until metas.length()) {
                                                    val m = metas.getJSONObject(i)
                                                    val mId = m.optString("id")
                                                    val mName = m.optString("name")
                                                    val mType = m.optString("type", cType)
                                                    val mPoster = m.optString("poster")
                                                    val mYear = m.optString("year", "")
                                                    val mRating = m.optString("imdbRating", "8.2")
                                                    val mDesc = m.optString("description", "")
                                                    val isSeries = mType == "series" || mType == "tv"
                                                    if (mName.isNotBlank()) {
                                                        cinemetaItems.add(
                                                            MediaItem(
                                                                id = mId,
                                                                title = mName,
                                                                category = if (isSeries) "Series" else "Movies",
                                                                imageUrl = mPoster.ifBlank { "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80" },
                                                                rating = mRating.ifBlank { "8.0" },
                                                                year = mYear.take(4).ifBlank { "2024" },
                                                                description = mDesc,
                                                                streamUrl = "",
                                                                episodes = if (isSeries) "All Episodes" else "",
                                                                isStreamable = true,
                                                                imdbId = mId,
                                                                type = if (isSeries) "series" else "movie"
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            if (cinemetaItems.isNotEmpty()) break
                        }
                        cinemetaItems
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                // 4. Query Anikoto Scraper in parallel (including shippuden variations)
                val anikotoDeferred = async(Dispatchers.IO) {
                    try {
                        val animeCandidates = linkedSetOf(
                            trimmed,
                            correctedQuery,
                            "naruto shippuuden",
                            primaryToken
                        ).filter { it.isNotBlank() }

                        var rawAnime = emptyList<com.example.scraper.AnikotoAnimeItem>()
                        for (cand in animeCandidates) {
                            try {
                                val found = com.example.scraper.AnikotoScraper.searchOrFilterAnime(keyword = cand)
                                if (found.isNotEmpty()) {
                                    rawAnime = found
                                    break
                                }
                            } catch (_: Exception) {}
                        }

                        if (rawAnime.isEmpty()) {
                            try {
                                rawAnime = com.example.scraper.AnikotoScraper.getLiveSuggestions(trimmed)
                            } catch (_: Exception) {}
                        }

                        rawAnime.filter { item ->
                            val isMovie = item.type.lowercase().contains("movie")
                            when (type) {
                                "movie" -> isMovie
                                "series", "tv" -> !isMovie
                                else -> true
                            }
                        }.map { item ->
                            val isMovie = item.type.lowercase().contains("movie")
                            val poster = com.example.scraper.AnikotoScraper.sanitizePosterUrl(item.posterUrl)
                            MediaItem(
                                id = "anikoto_${item.id}",
                                title = item.title,
                                category = if (isMovie) "Anime Movies" else "Anime Series",
                                imageUrl = poster.ifBlank { if (isMovie) "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80" else "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80" },
                                rating = if (item.rating.isNotBlank()) item.rating else "8.3",
                                year = if (item.releaseYear.isNotBlank()) item.releaseYear else "2024",
                                description = item.description,
                                streamUrl = item.watchUrl,
                                episodes = if (!isMovie) item.episodesInfo else "",
                                isStreamable = true,
                                imdbId = "anikoto_${item.id}",
                                type = if (isMovie) "movie" else "series"
                            )
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                val anikotoResults = anikotoDeferred.await()
                val cinemetaResults = cinemetaDeferred.await()
                val tmdbResults = tmdbDeferred.await()

                // Combine all sources: Anikoto (Anime prioritized) + Cinemeta + TMDB
                (anikotoResults + cinemetaResults + tmdbResults).distinctBy { it.id }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
