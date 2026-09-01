package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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
     * Queries multiple subtitle providers (Stremio OpenSubtitles v3, Stremio v1, Wyzie, SubDL) concurrently in parallel.
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

        val cleanNumericTmdbId = tmdbId.trim()
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

        // Fetch concurrently across all subtitle providers
        coroutineScope {
            val deferredList = listOf(
                // Provider 1: Stremio OpenSubtitles v3
                async {
                    fetchStremioOpenSubtitles(effectiveImdbId, isTv, season, episode)
                },
                // Provider 2: Stremio Official Subtitles v1
                async {
                    fetchStremioV1Subtitles(effectiveImdbId, isTv, season, episode)
                },
                // Provider 3: Wyzie Subtitles Provider
                async {
                    fetchWyzieSubtitles(cleanNumericTmdbId, isTv, season, episode)
                },
                // Provider 4: SubDL Subtitles Provider
                async {
                    fetchSubDlSubtitles(effectiveImdbId, cleanNumericTmdbId, title, isTv, season, episode)
                }
            )

            val results = deferredList.awaitAll()
            for (subList in results) {
                for (sub in subList) {
                    if (sub.url.isNotBlank() && !seenUrls.contains(sub.url)) {
                        seenUrls.add(sub.url)
                        tracks.add(sub)
                    }
                }
            }
        }

        // Deduplicate and order with Bengali & English first, then alphabetically
        val sortedTracks = tracks.sortedWith(
            compareByDescending<SubtitleTrack> { it.default }
                .thenBy {
                    when {
                        it.lang.startsWith("bn", ignoreCase = true) || it.label.contains("Bengali", ignoreCase = true) || it.label.contains("বাংলা") -> 0
                        it.lang.startsWith("en", ignoreCase = true) || it.label.contains("English", ignoreCase = true) -> 1
                        it.lang.startsWith("hi", ignoreCase = true) || it.label.contains("Hindi", ignoreCase = true) || it.label.contains("हिंदी") -> 2
                        it.lang.startsWith("es", ignoreCase = true) || it.label.contains("Spanish", ignoreCase = true) -> 3
                        it.lang.startsWith("ar", ignoreCase = true) || it.label.contains("Arabic", ignoreCase = true) -> 4
                        else -> 5
                    }
                }
                .thenBy { it.label }
        )

        Log.d(TAG, "Fetched ${sortedTracks.size} subtitles for $title (TMDB: $cleanNumericTmdbId, IMDb: $effectiveImdbId)")
        sortedTracks
    }

    private fun fetchStremioOpenSubtitles(
        effectiveImdbId: String,
        isTv: Boolean,
        season: Int,
        episode: Int
    ): List<SubtitleTrack> {
        if (effectiveImdbId.isBlank()) return emptyList()
        val result = mutableListOf<SubtitleTrack>()
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
                        if (url.isNotBlank()) {
                            result.add(
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
        return result
    }

    private fun fetchStremioV1Subtitles(
        effectiveImdbId: String,
        isTv: Boolean,
        season: Int,
        episode: Int
    ): List<SubtitleTrack> {
        if (effectiveImdbId.isBlank()) return emptyList()
        val result = mutableListOf<SubtitleTrack>()
        try {
            val subUrl = if (isTv) {
                "https://subtitles.strem.io/stremio/v1/subtitles/series/$effectiveImdbId:$season:$episode.json"
            } else {
                "https://subtitles.strem.io/stremio/v1/subtitles/movie/$effectiveImdbId.json"
            }

            val req = Request.Builder()
                .url(subUrl)
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
                        if (url.isNotBlank()) {
                            result.add(
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
            // Ignore optional fallback error
        }
        return result
    }

    private fun fetchWyzieSubtitles(
        cleanNumericTmdbId: String,
        isTv: Boolean,
        season: Int,
        episode: Int
    ): List<SubtitleTrack> {
        if (!cleanNumericTmdbId.all { it.isDigit() } || cleanNumericTmdbId.isEmpty()) return emptyList()
        val result = mutableListOf<SubtitleTrack>()
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
                    val display = obj.optString("display", "")
                    val label = if (display.isNotBlank()) display else getLanguageLabel(lang)
                    if (url.isNotBlank()) {
                        result.add(
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
        } catch (e: Exception) {
            Log.w(TAG, "Wyzie subtitle fetch failed: ${e.message}")
        }
        return result
    }

    private fun fetchSubDlSubtitles(
        effectiveImdbId: String,
        cleanNumericTmdbId: String,
        title: String,
        isTv: Boolean,
        season: Int,
        episode: Int
    ): List<SubtitleTrack> {
        val result = mutableListOf<SubtitleTrack>()
        try {
            val subDlUrl = when {
                effectiveImdbId.isNotBlank() -> {
                    val typeParam = if (isTv) "tv" else "movie"
                    if (isTv) "https://api.subdl.com/api/v1/subtitles?imdb_id=$effectiveImdbId&type=$typeParam&season=$season&episode=$episode"
                    else "https://api.subdl.com/api/v1/subtitles?imdb_id=$effectiveImdbId&type=$typeParam"
                }
                cleanNumericTmdbId.all { it.isDigit() } && cleanNumericTmdbId.isNotBlank() -> {
                    val typeParam = if (isTv) "tv" else "movie"
                    if (isTv) "https://api.subdl.com/api/v1/subtitles?tmdb_id=$cleanNumericTmdbId&type=$typeParam&season=$season&episode=$episode"
                    else "https://api.subdl.com/api/v1/subtitles?tmdb_id=$cleanNumericTmdbId&type=$typeParam"
                }
                title.isNotBlank() -> {
                    val enc = URLEncoder.encode(title, "UTF-8")
                    "https://api.subdl.com/api/v1/subtitles?film_name=$enc"
                }
                else -> null
            } ?: return emptyList()

            val req = Request.Builder()
                .url(subDlUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "application/json")
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val status = json.optBoolean("status", false)
                if (status) {
                    val subs = json.optJSONArray("subtitles")
                    if (subs != null) {
                        for (i in 0 until minOf(subs.length(), 20)) {
                            val item = subs.getJSONObject(i)
                            val rawUrl = item.optString("url", "")
                            val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "https://dl.subdl.com$rawUrl"
                            val lang = item.optString("lang", item.optString("language", "en"))
                            val label = getLanguageLabel(lang)
                            if (rawUrl.isNotBlank()) {
                                result.add(
                                    SubtitleTrack(
                                        url = fullUrl,
                                        lang = lang,
                                        label = label,
                                        default = lang == "en" || lang == "eng"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore optional subdl errors
        }
        return result
    }

    fun getLanguageLabel(langCode: String): String {
        val code = langCode.trim().lowercase()
            .removePrefix("language_")
            .trim()

        return when {
            code == "bn" || code == "ben" || code == "bangla" || code == "bengali" -> "Bengali (বাংলা)"
            code == "en" || code == "eng" || code == "english" -> "English"
            code == "hi" || code == "hin" || code == "hindi" -> "Hindi (हिंदी)"
            code == "es" || code == "spa" || code == "spanish" || code == "es-la" || code == "es-es" -> "Spanish (Español)"
            code == "fr" || code == "fre" || code == "fra" || code == "french" -> "French (Français)"
            code == "de" || code == "ger" || code == "deu" || code == "german" -> "German (Deutsch)"
            code == "it" || code == "ita" || code == "italian" -> "Italian (Italiano)"
            code == "pt" || code == "por" || code == "portuguese" || code == "pt-br" || code == "pt-pt" -> "Portuguese (Português)"
            code == "ru" || code == "rus" || code == "russian" -> "Russian (Русский)"
            code == "ar" || code == "ara" || code == "arabic" -> "Arabic (العربية)"
            code == "ja" || code == "jpn" || code == "japanese" -> "Japanese (日本語)"
            code == "ko" || code == "kor" || code == "korean" -> "Korean (한국어)"
            code == "zh" || code == "chi" || code == "zho" || code == "chinese" || code == "zh-cn" || code == "zh-tw" -> "Chinese (中文)"
            code == "id" || code == "ind" || code == "indonesian" -> "Indonesian (Bahasa Indonesia)"
            code == "ms" || code == "may" || code == "msa" || code == "malay" -> "Malay (Bahasa Melayu)"
            code == "vi" || code == "vie" || code == "vietnamese" -> "Vietnamese (Tiếng Việt)"
            code == "th" || code == "tha" || code == "thai" -> "Thai (ไทย)"
            code == "tr" || code == "tur" || code == "turkish" -> "Turkish (Türkçe)"
            code == "ur" || code == "urd" || code == "urdu" -> "Urdu (اردو)"
            code == "fa" || code == "per" || code == "fas" || code == "persian" || code == "farsi" -> "Persian (فارسی)"
            code == "ta" || code == "tam" || code == "tamil" -> "Tamil (தமிழ்)"
            code == "te" || code == "tel" || code == "telugu" -> "Telugu (తెలుగు)"
            code == "ml" || code == "mal" || code == "malayalam" -> "Malayalam (മലയാളം)"
            code == "tl" || code == "tgl" || code == "fil" || code == "filipino" || code == "tagalog" -> "Filipino (Tagalog)"
            code == "pl" || code == "pol" || code == "polish" -> "Polish (Polski)"
            code == "nl" || code == "dut" || code == "nld" || code == "dutch" -> "Dutch (Nederlands)"
            code == "sv" || code == "swe" || code == "swedish" -> "Swedish (Svenska)"
            code == "ro" || code == "ron" || code == "rum" || code == "romanian" -> "Romanian (Română)"
            code == "el" || code == "ell" || code == "gre" || code == "greek" -> "Greek (Ελληνικά)"
            code == "hu" || code == "hun" || code == "hungarian" -> "Hungarian (Magyar)"
            code == "cs" || code == "ces" || code == "cze" || code == "czech" -> "Czech (Čeština)"
            code == "da" || code == "dan" || code == "danish" -> "Danish (Dansk)"
            code == "fi" || code == "fin" || code == "finnish" -> "Finnish (Suomi)"
            code == "he" || code == "heb" || code == "hebrew" -> "Hebrew (עברית)"
            code == "uk" || code == "ukr" || code == "ukrainian" -> "Ukrainian (Українська)"
            code == "no" || code == "nor" || code == "norwegian" -> "Norwegian (Norsk)"
            code.length in 2..3 -> code.uppercase()
            else -> code.replaceFirstChar { it.uppercase() }
        }
    }
}

