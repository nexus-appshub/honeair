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
        val isExplicitTv = result.media_type == "tv" ||
                (result.name != null && result.title == null) ||
                (result.first_air_date != null && result.release_date == null) ||
                category.contains("Series", ignoreCase = true) ||
                category.contains("TV", ignoreCase = true) ||
                category.contains("K-Drama", ignoreCase = true) ||
                category.contains("Kdrama", ignoreCase = true)
        val isExplicitMovie = result.media_type == "movie" ||
                (result.title != null && result.name == null) ||
                (result.release_date != null && result.first_air_date == null) ||
                category.contains("Movie", ignoreCase = true) ||
                category.contains("Cinema", ignoreCase = true)

        val resolvedType = when (type) {
            "series", "tv" -> "series"
            "movie" -> "movie"
            else -> if (isExplicitTv) "series" else if (isExplicitMovie) "movie" else if (result.name != null) "series" else "movie"
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
            imdbId = tmdbId, // Using TMDB ID as imdbId since vidsrc supports tmdb id too
            type = resolvedType
        )
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
        // All anime in Latest is sourced purely from Anikoto
        jobs.add(async(Dispatchers.IO) {
            try {
                val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(sortBy = "latest-updated", page = page)
                list.map { item ->
                    val isMovie = item.type.lowercase().contains("movie")
                    val type = if (isMovie) "movie" else "series"
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = if (isMovie) "Latest Anime Movies" else "Latest Anime Series",
                        imageUrl = item.posterUrl,
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
        addJob("Latest", "movie") { tmdbApi.getTrendingToday(page = it) }

        val results = jobs.awaitAll().flatten().filter { it.imageUrl.isNotEmpty() }.distinctBy { it.id }
        results.sortedWith(
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
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Series",
                        imageUrl = item.posterUrl,
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
                val animeMovies = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "Movie", sortBy = "latest-updated", page = 1)
                animeMovies.map { item ->
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Movies",
                        imageUrl = item.posterUrl,
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

        val fetchedList = jobs.awaitAll().flatten().toMutableList()

            // Top Curated Popular Bangla OTT Web Series, Natok & Movies
            val curatedBanglaItems = listOf(
                MediaItem(
                    id = "movie_2026_01",
                    title = "Dor ( দোর )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
                    rating = "9.2",
                    year = "2026",
                    description = "Latest 2026 blockbuster emotional thriller exploring deep family bonds and unexpected twists in Dhaka.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/tt18281358",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "tt18281358",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_2026_02",
                    title = "Toofan 2: The Legacy ( তুফান ২ )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500&q=80",
                    rating = "9.0",
                    year = "2026",
                    description = "The explosive 2026 sequel continuing the underworld empire saga with high-octane action and dramatic twists.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/tt31526487",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "tt31526487",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_2026_03",
                    title = "Bonolota Express ( বনলতা এক্সপ্রেস )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80",
                    rating = "9.1",
                    year = "2026",
                    description = "A phenomenal 2026 action-comedy directed by Raihan Rafi featuring an elite ensemble cast of Chanchal Chowdhury and Mosharraf Karim.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/tt31526487",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "tt31526487",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_2025_01",
                    title = "Devi Chowdhurani: Bandit Queen ( দেবী চৌধুরানী )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
                    rating = "9.4",
                    year = "2025",
                    description = "The epic 2025 period action-adventure film based on Bankim Chandra Chatterjee's masterpiece, depicting Profulla's rise as a legendary outlaw queen.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/tt27715694",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "tt27715694",
                    type = "movie"
                ),
                MediaItem(
                    id = "series_2025_01",
                    title = "Chakrabuhy ( চক্রব্যূহ )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
                    rating = "8.9",
                    year = "2025",
                    description = "A gripping 2025 political crime thriller series uncovering massive corporate corruption and secret conspiracies.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/tt14917538/1/1",
                    episodes = "Season 1",
                    isStreamable = true,
                    imdbId = "tt14917538",
                    type = "series"
                ),
                MediaItem(
                    id = "series_117282",
                    title = "Karagar ( কারাগার )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?w=500&q=80",
                    rating = "8.8",
                    year = "2022",
                    description = "A mysterious prisoner appears inside Cell No. 145 of Akashgar High Security Jail, claiming to have been imprisoned for over 250 years.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/tt21815124/1/1",
                    episodes = "Season 1",
                    isStreamable = true,
                    imdbId = "tt21815124",
                    type = "series"
                ),
                MediaItem(
                    id = "series_128833",
                    title = "Mohanagar ( মহানগর )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1535016120720-40c646be5580?w=500&q=80",
                    rating = "8.9",
                    year = "2021",
                    description = "Inside a Dhaka police station, a fateful night unfolds involving an influential politician's son, an honest officer, and a corrupt system.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/tt14917538/1/1",
                    episodes = "Season 1",
                    isStreamable = true,
                    imdbId = "tt14917538",
                    type = "series"
                ),
                MediaItem(
                    id = "series_113886",
                    title = "Taqdeer ( তাকদীর )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&q=80",
                    rating = "8.7",
                    year = "2020",
                    description = "A freezer van driver finds an unidentified corpse inside his vehicle, unraveling a high-stakes conspiracy.",
                    streamUrl = "https://vidsrc.sbs/embed/tv/tt13645376/1/1",
                    episodes = "Season 1",
                    isStreamable = true,
                    imdbId = "tt13645376",
                    type = "series"
                ),
                MediaItem(
                    id = "movie_838209",
                    title = "Hawa ( হাওয়া )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
                    rating = "8.2",
                    year = "2022",
                    description = "A group of fishermen in the Bay of Bengal catch a mysterious girl in their net, triggering paranoia and supernatural occurrences.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/tt18281358",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "tt18281358",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_1136371",
                    title = "Toofan ( তুফান )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&q=80",
                    rating = "8.5",
                    year = "2024",
                    description = "An infamous 1990s Bangladeshi gangster rises to power, ruling the underworld with high-octane action.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/tt31526487",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "tt31526487",
                    type = "movie"
                ),
                MediaItem(
                    id = "movie_1139088",
                    title = "Priyotoma ( প্রিয়তমা )",
                    category = "Bangla Cinema & Natok",
                    imageUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500&q=80",
                    rating = "8.0",
                    year = "2023",
                    description = "An emotional romantic action drama depicting a lover's journey through time, sacrifices, and destiny.",
                    streamUrl = "https://vidsrc.sbs/embed/movie/tt27715694",
                    episodes = "",
                    isStreamable = true,
                    imdbId = "tt27715694",
                    type = "movie"
                )
            )
            fetchedList.addAll(curatedBanglaItems)
            fetchedList.retainAll { it.imageUrl.isNotEmpty() }
            fetchedList.distinctBy { it.id }
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
        val tmdbType = if (type == "series" || type == "tv") "tv" else "movie"
        var finalId = id
        if (finalId.startsWith("movie_") || finalId.startsWith("series_")) {
            finalId = finalId.substringAfter("_")
        }
        detailCache[finalId]?.let { return@withContext it }

        if (finalId.startsWith("tt")) {
            try {
                val findResponse = tmdbApi.getByExternalId(finalId)
                val resolvedId = if (tmdbType == "movie") {
                    findResponse.movie_results?.firstOrNull()?.id?.toString()
                } else {
                    findResponse.tv_results?.firstOrNull()?.id?.toString()
                }
                if (resolvedId != null) {
                    finalId = resolvedId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        detailCache[finalId]?.let { return@withContext it }

        val details = tmdbApi.getMediaDetails(type = tmdbType, id = finalId)
        detailCache[finalId] = details
        details
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
                            MediaItem(
                                id = "anikoto_${item.id}",
                                title = item.title,
                                category = "Anime Series",
                                imageUrl = item.posterUrl,
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
            }
            catLower.contains("anime movies") -> {
                jobs.add(async(Dispatchers.IO) {
                    try {
                        val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "Movie", page = page)
                        list.map { item ->
                            MediaItem(
                                id = "anikoto_${item.id}",
                                title = item.title,
                                category = "Anime Movies",
                                imageUrl = item.posterUrl,
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
            }
            catLower.contains("k-drama") || catLower.contains("kdrama") -> addJob("K-Dramas", "series") { tmdbApi.getKDramas(page = it) }
            catLower == "action" -> addJob("Action", "movie") { tmdbApi.getActionMovies(page = it) }
            catLower.contains("sci-fi") || catLower.contains("scifi") -> addJob("Sci-Fi", "movie") { tmdbApi.getSciFiMovies(page = it) }
            catLower.contains("hindi cinema") || catLower.contains("hindi movie") -> addJob("Hindi Cinema", "movie") { tmdbApi.getHindiMovies(page = it) }
            catLower.contains("hindi series") -> addJob("Hindi Series", "series") { tmdbApi.getHindiSeries(page = it) }
            catLower.contains("hindi dubbed k-drama") || catLower.contains("hindi dubbed kdrama") -> addJob("Hindi Dubbed K-Dramas", "series") { tmdbApi.getHindiDubbedKDramas(page = it) }
            catLower.contains("hindi dubbed") || catLower.contains("dubbed") -> addJob("Hindi Dubbed", "movie") { tmdbApi.getHindiDubbedMovies(page = it) }
            catLower.contains("bangla") || catLower.contains("bengali") -> {
                addJob("Bangla Cinema & Natok", "movie") { tmdbApi.getBengaliMovies(page = it) }
                addJob("Bangla Cinema & Natok", "series") { tmdbApi.getBengaliSeries(page = it) }
            }
            else -> {
                // Fallback core categories
                jobs.add(async(Dispatchers.IO) { fetchLatestReleases(page) })
                addJob("Movies", "movie") { tmdbApi.getTrendingMovies(page = it) }
                addJob("Series & TV Shows", "series") { tmdbApi.getTrendingTvShows(page = it) }
            }
        }
        jobs.awaitAll().flatten().filter { it.imageUrl.isNotEmpty() }.distinctBy { it.id }
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
        addJob("Bangla Cinema & Natok", "movie") { tmdbApi.getBengaliMovies(page = it) }
        addJob("Bangla Cinema & Natok", "series") { tmdbApi.getBengaliSeries(page = it) }
        jobs.add(async(Dispatchers.IO) {
            try {
                val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "TV", page = page)
                list.map { item ->
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Series",
                        imageUrl = item.posterUrl,
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
                val list = com.example.scraper.AnikotoScraper.searchOrFilterAnime(type = "Movie", page = page)
                list.map { item ->
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = "Anime Movies",
                        imageUrl = item.posterUrl,
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
 
        val fetchedList = jobs.awaitAll().flatten().filter { it.imageUrl.isNotEmpty() }.distinctBy { it.id }
        fetchedList
    }

    suspend fun searchMedia(query: String, type: String = "all"): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val tmdbResults = if (type == "movie") {
                tmdbApi.searchMovies(query = query).results ?: emptyList()
            } else if (type == "series" || type == "tv") {
                tmdbApi.searchTvShows(query = query).results ?: emptyList()
            } else {
                val m = tmdbApi.searchMovies(query = query).results ?: emptyList()
                val t = tmdbApi.searchTvShows(query = query).results ?: emptyList()
                m + t
            }
            // Filter out any anime from TMDB results
            val nonAnimeTmdbResults = tmdbResults.filter { !isTmdbAnime(it) }
            val mappedTmdb = nonAnimeTmdbResults.take(20).map { result ->
                val requestedType = if (type == "movie" || type == "series" || type == "tv") type else "auto"
                mapToMediaItem(result, "Search", requestedType) 
            }
            
            // Query Anikoto to guarantee accurate matching for anime titles
            val anikotoResults = try {
                var rawAnime = com.example.scraper.AnikotoScraper.searchOrFilterAnime(keyword = query)
                if (rawAnime.isEmpty()) {
                    rawAnime = com.example.scraper.AnikotoScraper.getLiveSuggestions(query)
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
                    MediaItem(
                        id = "anikoto_${item.id}",
                        title = item.title,
                        category = if (isMovie) "Anime Movies" else "Anime Series",
                        imageUrl = item.posterUrl,
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
            } catch (e: Exception) {
                emptyList()
            }

            // Prepend Anikoto results so they appear first and are highly relevant
            (anikotoResults + mappedTmdb).distinctBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
