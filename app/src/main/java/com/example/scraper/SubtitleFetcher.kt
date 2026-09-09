package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SubtitleFetcher {
    private const val TAG = "SubtitleFetcher"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Universal automated subtitle fetching for movies, series, and anime.
     * Queries multiple subtitle providers (Stremio OpenSubtitles, Wyzie, Subscene) in parallel.
     */
    suspend fun fetchSubtitles(
        tmdbId: String,
        imdbId: String? = null,
        title: String = "",
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SubtitleTrack>()
        val seenUrls = mutableSetOf<String>()

        var effectiveImdbId = imdbId?.trim() ?: ""
        if (effectiveImdbId.isBlank() && tmdbId.startsWith("tt")) {
            effectiveImdbId = tmdbId.trim()
        }

        var cleanNumericTmdbId = tmdbId.trim()
            .removePrefix("movie_")
            .removePrefix("series_")
            .removePrefix("anikoto_")
            .trim()

        // 1. Resolve IMDb ID from TMDB if missing
        if (effectiveImdbId.isBlank() && cleanNumericTmdbId.all { it.isDigit() } && cleanNumericTmdbId.isNotEmpty()) {
            try {
                val tmdbType = if (isTv) "tv" else "movie"
                val extUrl = "https://api.themoviedb.org/3/$tmdbType/$cleanNumericTmdbId/external_ids?api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                val req = Request.Builder()
                    .url(extUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful && body.isNotBlank()) {
                    val json = JSONObject(body)
                    val foundImdb = json.optString("imdb_id", "")
                    if (foundImdb.isNotBlank()) {
                        effectiveImdbId = foundImdb
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve IMDb ID for subtitles: ${e.message}")
            }
        }

        // Provider 1: Stremio OpenSubtitles v3
        if (effectiveImdbId.isNotBlank()) {
            try {
                val stremioSubUrl = if (isTv) {
                    "https://opensubtitles-v3.strem.io/subtitles/series/$effectiveImdbId:$season:$episode.json"
                } else {
                    "https://opensubtitles-v3.strem.io/subtitles/movie/$effectiveImdbId.json"
                }

                val req = Request.Builder()
                    .url(stremioSubUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Accept", "application/json")
                    .build()

                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful && body.isNotBlank()) {
                    val root = JSONObject(body)
                    val subtitlesArr = root.optJSONArray("subtitles")
                    if (subtitlesArr != null) {
                        for (i in 0 until subtitlesArr.length()) {
                            val sub = subtitlesArr.getJSONObject(i)
                            val url = sub.optString("url", "")
                            val lang = sub.optString("lang", "en")
                            val label = getLanguageLabel(lang)
                            if (url.isNotBlank() && !seenUrls.contains(url)) {
                                seenUrls.add(url)
                                tracks.add(
                                    SubtitleTrack(
                                        url = url,
                                        lang = lang,
                                        label = label,
                                        default = lang == "en" || lang == "eng"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenSubtitles fetch failed: ${e.message}")
            }
        }

        // Provider 2: Wyzie Subtitles Provider
        if (cleanNumericTmdbId.all { it.isDigit() } && cleanNumericTmdbId.isNotEmpty()) {
            try {
                val wyzieUrl = if (isTv) {
                    "https://sub.wyzie.ru/search?id=$cleanNumericTmdbId&season=$season&episode=$episode"
                } else {
                    "https://sub.wyzie.ru/search?id=$cleanNumericTmdbId"
                }

                val req = Request.Builder()
                    .url(wyzieUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Accept", "application/json")
                    .build()

                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful && body.isNotBlank() && body.trim().startsWith("[")) {
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val url = obj.optString("url", "").ifEmpty { obj.optString("file", "") }
                        val lang = obj.optString("lang", "").ifEmpty { obj.optString("language", "en") }
                        val label = obj.optString("display", "").ifEmpty { getLanguageLabel(lang) }
                        if (url.isNotBlank() && !seenUrls.contains(url)) {
                            seenUrls.add(url)
                            tracks.add(
                                SubtitleTrack(
                                    url = url,
                                    lang = lang,
                                    label = label,
                                    default = (lang == "en" || lang == "eng") && tracks.none { it.default }
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Wyzie subtitle fetch failed: ${e.message}")
            }
        }

        // Deduplicate and order with English and common languages first
        val sortedTracks = tracks.sortedWith(
            compareByDescending<SubtitleTrack> { it.default }
                .thenBy { if (it.lang.startsWith("en", ignoreCase = true)) 0 else 1 }
                .thenBy { it.label }
        )

        Log.d(TAG, "Fetched ${sortedTracks.size} subtitles for $title (TMDB: $cleanNumericTmdbId, IMDb: $effectiveImdbId)")
        sortedTracks
    }

    private fun getLanguageLabel(langCode: String): String {
        val code = langCode.trim().lowercase()
        return when (code) {
            "en", "eng" -> "English"
            "es", "spa" -> "Spanish"
            "fr", "fre", "fra" -> "French"
            "de", "ger", "deu" -> "German"
            "it", "ita" -> "Italian"
            "pt", "por", "pt-br" -> "Portuguese"
            "ru", "rus" -> "Russian"
            "hi", "hin" -> "Hindi"
            "bn", "ben" -> "Bengali"
            "ar", "ara" -> "Arabic"
            "ja", "jpn" -> "Japanese"
            "ko", "kor" -> "Korean"
            "zh", "chi", "zho" -> "Chinese"
            "id", "ind" -> "Indonesian"
            "vi", "vie" -> "Vietnamese"
            "th", "tha" -> "Thai"
            "tr", "tur" -> "Turkish"
            "pl", "pol" -> "Polish"
            "nl", "dut", "nld" -> "Dutch"
            else -> code.uppercase()
        }
    }
}
