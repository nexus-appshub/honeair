package com.example.scraper

import android.util.Log
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Universal Movie & TV Series Poster Engine
 * Automatically fetches and caches high-definition posters from TMDB & Cinemeta
 * for all standard movies, series, natoks, and foreign films.
 */
object MovieSeriesPosterEngine {
    private const val TAG = "MovieSeriesPosterEngine"
    private const val TMDB_API_KEY = "a359b11d9aa4c4803d25ef86cf7fb19c"

    // High performance in-memory cache for resolved poster URLs
    private val posterCache = ConcurrentHashMap<String, String>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Cleans a movie or series title for optimal metadata matching
     */
    fun cleanTitle(title: String): String {
        return title
            .removePrefix("movie_")
            .removePrefix("series_")
            .removePrefix("anikoto_")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""(?i)\b(1080p|720p|4k|uhd|fhd|hd|bd|web-dl|bluray|x264|x265|hevc|hindi|bangla|english|dubbed|subbed)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Resolves high-resolution poster for a given movie/series title, type, and year.
     */
    suspend fun resolvePoster(title: String, type: String = "auto", year: String = "", id: String = ""): String? = withContext(Dispatchers.IO) {
        val clean = cleanTitle(title)
        val cleanId = id.removePrefix("movie_").removePrefix("series_").removePrefix("anikoto_").trim()

        if (clean.isBlank() && cleanId.isBlank()) return@withContext null

        val cacheKey = if (clean.isNotBlank()) "${clean.lowercase()}_$type" else "${cleanId.lowercase()}_$type"
        posterCache[cacheKey]?.let { return@withContext it }

        // Tier 1: If cleanId is an IMDb ID (tt...) or numeric TMDB ID, resolve directly
        if (cleanId.isNotBlank()) {
            val directPoster = fetchDirectById(cleanId, type)
            if (!directPoster.isNullOrBlank()) {
                posterCache[cacheKey] = directPoster
                return@withContext directPoster
            }
        }

        // Tier 2: Search TMDB Search API
        if (clean.isNotBlank()) {
            val tmdbPoster = fetchFromTmdb(clean, type, year)
            if (!tmdbPoster.isNullOrBlank()) {
                posterCache[cacheKey] = tmdbPoster
                return@withContext tmdbPoster
            }
        }

        // Tier 3: Search Cinemeta Catalog API Fallback
        if (clean.isNotBlank()) {
            val cinemetaPoster = fetchFromCinemeta(clean, type)
            if (!cinemetaPoster.isNullOrBlank()) {
                posterCache[cacheKey] = cinemetaPoster
                return@withContext cinemetaPoster
            }
        }

        null
    }

    private suspend fun fetchDirectById(id: String, type: String): String? = withContext(Dispatchers.IO) {
        try {
            if (id.startsWith("tt")) {
                // Find by external IMDb ID
                val url = "https://api.themoviedb.org/3/find/$id?external_source=imdb_id&api_key=$TMDB_API_KEY"
                val req = Request.Builder().url(url).header("Accept", "application/json").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val movieResults = json.optJSONArray("movie_results")
                        val tvResults = json.optJSONArray("tv_results")
                        val item = (if (type == "series" || type == "tv") tvResults?.optJSONObject(0) ?: movieResults?.optJSONObject(0) else movieResults?.optJSONObject(0) ?: tvResults?.optJSONObject(0))
                        if (item != null) {
                            val poster = item.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
                                ?: item.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
                            if (!poster.isNullOrBlank()) {
                                return@withContext "https://image.tmdb.org/t/p/w500$poster"
                            }
                        }
                    }
                }
                // Cinemeta direct meta lookup by IMDb ID
                val cinemetaType = if (type == "series" || type == "tv") "series" else "movie"
                val cinemetaUrl = "https://v3-cinemeta.strem.io/meta/$cinemetaType/$id.json"
                val cReq = Request.Builder().url(cinemetaUrl).header("Accept", "application/json").build()
                httpClient.newCall(cReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val meta = json.optJSONObject("meta")
                        val poster = meta?.optString("poster", "")?.takeIf { it.isNotBlank() && it != "null" }
                        if (!poster.isNullOrBlank()) {
                            return@withContext poster
                        }
                    }
                }
            } else if (id.all { it.isDigit() }) {
                // Numeric TMDB ID
                val tmdbType = if (type == "series" || type == "tv") "tv" else "movie"
                val url = "https://api.themoviedb.org/3/$tmdbType/$id?api_key=$TMDB_API_KEY"
                val req = Request.Builder().url(url).header("Accept", "application/json").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val poster = json.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
                            ?: json.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
                        if (!poster.isNullOrBlank()) {
                            return@withContext "https://image.tmdb.org/t/p/w500$poster"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct ID poster fetch error for '$id': ${e.message}")
        }
        null
    }

    private suspend fun fetchFromTmdb(query: String, type: String, year: String): String? = withContext(Dispatchers.IO) {
        try {
            val enc = URLEncoder.encode(query, "UTF-8")
            val yearParam = if (year.isNotBlank() && year.length == 4) "&year=$year" else ""
            val urlsToTry = mutableListOf<String>()
            
            when (type.lowercase()) {
                "movie" -> {
                    urlsToTry.add("https://api.themoviedb.org/3/search/movie?query=$enc$yearParam&api_key=$TMDB_API_KEY")
                    if (yearParam.isNotEmpty()) urlsToTry.add("https://api.themoviedb.org/3/search/movie?query=$enc&api_key=$TMDB_API_KEY")
                    urlsToTry.add("https://api.themoviedb.org/3/search/multi?query=$enc&api_key=$TMDB_API_KEY")
                }
                "series", "tv" -> {
                    urlsToTry.add("https://api.themoviedb.org/3/search/tv?query=$enc$yearParam&api_key=$TMDB_API_KEY")
                    if (yearParam.isNotEmpty()) urlsToTry.add("https://api.themoviedb.org/3/search/tv?query=$enc&api_key=$TMDB_API_KEY")
                    urlsToTry.add("https://api.themoviedb.org/3/search/multi?query=$enc&api_key=$TMDB_API_KEY")
                }
                else -> {
                    urlsToTry.add("https://api.themoviedb.org/3/search/multi?query=$enc&api_key=$TMDB_API_KEY")
                    urlsToTry.add("https://api.themoviedb.org/3/search/movie?query=$enc&api_key=$TMDB_API_KEY")
                    urlsToTry.add("https://api.themoviedb.org/3/search/tv?query=$enc&api_key=$TMDB_API_KEY")
                }
            }

            for (url in urlsToTry) {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HomeAirTV/4.7 (Android)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val json = JSONObject(bodyStr)
                    val results = json.optJSONArray("results") ?: return@use
                    if (results.length() == 0) return@use

                    for (i in 0 until minOf(results.length(), 5)) {
                        val obj = results.optJSONObject(i) ?: continue
                        val posterPath = obj.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
                            ?: obj.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
                        if (!posterPath.isNullOrBlank()) {
                            return@withContext "https://image.tmdb.org/t/p/w500$posterPath"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB poster fetch error for '$query': ${e.message}")
        }
        null
    }

    private suspend fun fetchFromCinemeta(query: String, type: String): String? = withContext(Dispatchers.IO) {
        try {
            val enc = URLEncoder.encode(query, "UTF-8")
            val typesToTry = when (type.lowercase()) {
                "movie" -> listOf("movie", "series")
                "series", "tv" -> listOf("series", "movie")
                else -> listOf("movie", "series")
            }

            for (cType in typesToTry) {
                val cinemetaUrl = "https://v3-cinemeta.strem.io/catalog/$cType/top/search=$enc.json"
                val request = Request.Builder()
                    .url(cinemetaUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HomeAirTV/4.7 (Android)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        val metas = json.optJSONArray("metas")
                        if (metas != null && metas.length() > 0) {
                            for (i in 0 until metas.length()) {
                                val m = metas.optJSONObject(i) ?: continue
                                val poster = m.optString("poster", "").takeIf { it.isNotBlank() && it != "null" }
                                if (!poster.isNullOrBlank()) {
                                    return@withContext poster
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cinemeta poster fetch error for '$query': ${e.message}")
        }
        null
    }

    /**
     * Enhances a list of MediaItem objects with accurate movie/series posters
     */
    suspend fun enhanceMediaItems(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val jobs = items.map { item ->
            async {
                val isAnime = AnimePosterEngine.isAnime(title = item.title, category = item.category, type = item.type, id = item.id)
                if (!isAnime && (item.imageUrl.isBlank() || item.imageUrl.contains("unsplash"))) {
                    val resolved = resolvePoster(item.title, item.type, item.year, item.imdbId ?: item.id)
                    if (!resolved.isNullOrBlank()) {
                        item.copy(imageUrl = resolved)
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
}
