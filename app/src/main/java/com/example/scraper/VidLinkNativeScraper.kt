package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VidLinkNativeScraper {
    private const val TAG = "VidLinkNativeScraper"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val VIDLINK_HOSTS = listOf(
        "https://vidlink.pro",
        "https://api.vidlink.pro",
        "https://vidlink.org",
        "https://vidlink.to",
        "https://vidlink.net",
        "https://vidlink.cc"
    )

    suspend fun extractStream(
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val cleanTmdb = tmdbId.removePrefix("movie_").removePrefix("series_").removePrefix("anikoto_").trim()
        if (cleanTmdb.isBlank()) return@withContext null

        val candidateTasks = mutableListOf<Pair<String, String>>() // (url, host)

        for (host in VIDLINK_HOSTS) {
            if (isTv) {
                candidateTasks.add(Pair("$host/api/b/tv/$cleanTmdb/$season/$episode", host))
                candidateTasks.add(Pair("$host/api/sources/tv/$cleanTmdb?s=$season&e=$episode", host))
                candidateTasks.add(Pair("$host/api/b/tv/$cleanTmdb?s=$season&e=$episode", host))
                candidateTasks.add(Pair("$host/tv/$cleanTmdb/$season/$episode", host))
            } else {
                candidateTasks.add(Pair("$host/api/b/movie/$cleanTmdb", host))
                candidateTasks.add(Pair("$host/api/sources/movie/$cleanTmdb", host))
                candidateTasks.add(Pair("$host/api/b/movie/$cleanTmdb?t=${System.currentTimeMillis()}", host))
                candidateTasks.add(Pair("$host/movie/$cleanTmdb", host))
            }
        }

        return@withContext coroutineScope {
            val resultChannel = Channel<ScrapedStreamResult>(10)
            val jobs = candidateTasks.map { (targetUrl, host) ->
                launch(Dispatchers.IO) {
                    try {
                        val isApi = targetUrl.contains("/api/")
                        val req = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", DEFAULT_UA)
                            .header("Referer", "$host/")
                            .header("Origin", host)
                            .header("Accept", if (isApi) "application/json, text/plain, */*" else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .build()

                        val resp = httpClient.newCall(req).execute()
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful && body.isNotBlank()) {
                            if (isApi) {
                                val stream = parseVidlinkResponse(body, host)
                                if (stream != null && stream.streamUrl.isNotBlank()) {
                                    Log.d(TAG, "VidLink API winner from $targetUrl: ${stream.streamUrl}")
                                    resultChannel.trySend(stream)
                                    return@launch
                                }
                            } else {
                                val stream = parseHtmlForStreams(body, host)
                                if (stream != null && stream.streamUrl.isNotBlank()) {
                                    Log.d(TAG, "VidLink HTML winner from $targetUrl: ${stream.streamUrl}")
                                    resultChannel.trySend(stream)
                                    return@launch
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            var winner: ScrapedStreamResult? = null
            try {
                winner = withTimeoutOrNull(10000L) {
                    resultChannel.receive()
                }
            } catch (_: Exception) {}

            jobs.forEach { it.cancel() }
            winner
        }
    }

    private fun parseVidlinkResponse(body: String, host: String): ScrapedStreamResult? {
        try {
            val json = JSONObject(body)
            // 1. Direct stream url fields
            var streamUrl = json.optString("stream", "")
            if (streamUrl.isBlank()) streamUrl = json.optString("url", "")
            if (streamUrl.isBlank()) streamUrl = json.optString("file", "")
            if (streamUrl.isBlank()) streamUrl = json.optString("streamUrl", "")

            // 2. Sources array
            val sources = json.optJSONArray("sources")
            if (streamUrl.isBlank() && sources != null && sources.length() > 0) {
                for (i in 0 until sources.length()) {
                    val src = sources.optJSONObject(i) ?: continue
                    val file = src.optString("url", src.optString("file", ""))
                    if (file.isNotBlank()) {
                        streamUrl = file
                        break
                    }
                }
            }

            // 3. Nested stream property
            if (streamUrl.isBlank()) {
                val streamObj = json.optJSONObject("stream")
                if (streamObj != null) {
                    val playlist = streamObj.optString("playlist", streamObj.optString("url", ""))
                    if (playlist.isNotBlank()) streamUrl = playlist
                }
            }

            // Subtitles parsing
            val subsList = mutableListOf<SubtitleTrack>()
            val tracks = json.optJSONArray("tracks") ?: json.optJSONArray("subtitles")
            if (tracks != null) {
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val file = t.optString("file", t.optString("url", ""))
                    val label = t.optString("label", t.optString("lang", "English"))
                    val isDef = t.optBoolean("default", false)
                    if (file.isNotBlank() && (file.endsWith(".vtt") || file.endsWith(".srt"))) {
                        subsList.add(
                            SubtitleTrack(
                                url = file,
                                label = label,
                                lang = label.take(2).lowercase(),
                                default = isDef
                            )
                        )
                    }
                }
            }

            if (streamUrl.isNotBlank() && (streamUrl.contains(".m3u8") || streamUrl.contains(".mp4") || streamUrl.startsWith("http"))) {
                return ScrapedStreamResult(
                    streamUrl = streamUrl,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to "$host/",
                        "Origin" to host
                    ),
                    referer = "$host/",
                    subtitles = subsList
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseVidlinkResponse error: ${e.message}")
        }
        return null
    }

    private fun parseHtmlForStreams(html: String, host: String): ScrapedStreamResult? {
        val m3u8Regex = Regex("""https?://[^\s"'<>\\]+?\.(?:m3u8|mp4)[^\s"'<>\\]*""")
        val matches = m3u8Regex.findAll(html).map { it.value }.toList()
        for (url in matches) {
            if (!url.contains("analytics") && !url.contains("favicon") && !url.contains("preview")) {
                return ScrapedStreamResult(
                    streamUrl = url,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to "$host/",
                        "Origin" to host
                    ),
                    referer = "$host/"
                )
            }
        }
        return null
    }
}
