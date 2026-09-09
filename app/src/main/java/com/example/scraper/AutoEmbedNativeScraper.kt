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

object AutoEmbedNativeScraper {
    private const val TAG = "AutoEmbedNativeScraper"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun extractStream(
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val cleanTmdb = tmdbId.removePrefix("movie_").removePrefix("series_").removePrefix("anikoto_").trim()
        val effectiveImdb = if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) imdbId.trim() else null

        val targets = mutableListOf<Triple<String, String, String>>() // (URL, Referer, Name)

        // 1. AutoEmbed CC
        if (isTv) {
            targets.add(Triple("https://player.autoembed.cc/embed/tv/$cleanTmdb/$season/$episode", "https://player.autoembed.cc/", "AutoEmbed TV"))
        } else {
            targets.add(Triple("https://player.autoembed.cc/embed/movie/$cleanTmdb", "https://player.autoembed.cc/", "AutoEmbed Movie"))
        }

        // 2. 2Embed
        if (isTv) {
            targets.add(Triple("https://www.2embed.cc/embedtv/$cleanTmdb&s=$season&e=$episode", "https://www.2embed.cc/", "2Embed TV"))
            if (!effectiveImdb.isNullOrBlank()) {
                targets.add(Triple("https://www.2embed.cc/embedtvfull/$effectiveImdb/$season/$episode", "https://www.2embed.cc/", "2Embed TV Full"))
            }
        } else {
            targets.add(Triple("https://www.2embed.cc/embed/$cleanTmdb", "https://www.2embed.cc/", "2Embed Movie"))
            if (!effectiveImdb.isNullOrBlank()) {
                targets.add(Triple("https://www.2embed.cc/embed/$effectiveImdb", "https://www.2embed.cc/", "2Embed Movie IMDb"))
            }
        }

        // 3. SmashyStream
        if (isTv) {
            targets.add(Triple("https://player.smashy.stream/tv/$cleanTmdb?s=$season&e=$episode", "https://player.smashy.stream/", "SmashyStream TV"))
            targets.add(Triple("https://embed.smashystream.com/playere.php?tmdb=$cleanTmdb&season=$season&episode=$episode", "https://embed.smashystream.com/", "SmashyStream PHP TV"))
        } else {
            targets.add(Triple("https://player.smashy.stream/movie/$cleanTmdb", "https://player.smashy.stream/", "SmashyStream Movie"))
            targets.add(Triple("https://embed.smashystream.com/playere.php?tmdb=$cleanTmdb", "https://embed.smashystream.com/", "SmashyStream PHP Movie"))
        }

        // 4. MultiEmbed Mov
        if (isTv) {
            targets.add(Triple("https://multiembed.mov/?video_id=$cleanTmdb&tmdb=1&s=$season&e=$episode", "https://multiembed.mov/", "MultiEmbed TV"))
            if (!effectiveImdb.isNullOrBlank()) {
                targets.add(Triple("https://multiembed.mov/?video_id=$effectiveImdb&s=$season&e=$episode", "https://multiembed.mov/", "MultiEmbed IMDb TV"))
            }
        } else {
            targets.add(Triple("https://multiembed.mov/?video_id=$cleanTmdb&tmdb=1", "https://multiembed.mov/", "MultiEmbed Movie"))
            if (!effectiveImdb.isNullOrBlank()) {
                targets.add(Triple("https://multiembed.mov/?video_id=$effectiveImdb", "https://multiembed.mov/", "MultiEmbed IMDb Movie"))
            }
        }

        // 5. Videasy / RiveStream
        if (isTv) {
            targets.add(Triple("https://player.videasy.net/tv/$cleanTmdb/$season/$episode", "https://player.videasy.net/", "Videasy TV"))
        } else {
            targets.add(Triple("https://player.videasy.net/movie/$cleanTmdb", "https://player.videasy.net/", "Videasy Movie"))
        }

        return@withContext coroutineScope {
            val resultChannel = Channel<ScrapedStreamResult>(10)
            val jobs = targets.map { (url, referer, name) ->
                launch(Dispatchers.IO) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", DEFAULT_UA)
                            .header("Referer", referer)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .build()

                        val resp = httpClient.newCall(req).execute()
                        val html = resp.body?.string() ?: ""
                        if (resp.isSuccessful && html.isNotBlank()) {
                            val stream = extractStreamFromHtml(html, referer)
                            if (stream != null && stream.streamUrl.isNotBlank()) {
                                Log.d(TAG, "[$name] winner from $url: ${stream.streamUrl}")
                                resultChannel.trySend(stream)
                                return@launch
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            var winner: ScrapedStreamResult? = null
            try {
                winner = withTimeoutOrNull(9000L) {
                    resultChannel.receive()
                }
            } catch (_: Exception) {}

            jobs.forEach { it.cancel() }
            winner
        }
    }

    private fun extractStreamFromHtml(html: String, referer: String): ScrapedStreamResult? {
        val m3u8Regex = Regex("""https?://[^\s"'<>\\]+?\.(?:m3u8|mp4)[^\s"'<>\\]*""")
        val matches = m3u8Regex.findAll(html).map { it.value }.toList()
        for (url in matches) {
            if (!url.contains("analytics") && !url.contains("favicon") && !url.contains("preview") && !url.contains("sample")) {
                val subs = extractSubtitlesFromHtml(html)
                return ScrapedStreamResult(
                    streamUrl = url,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to referer,
                        "Origin" to referer.removeSuffix("/")
                    ),
                    referer = referer,
                    subtitles = subs
                )
            }
        }

        try {
            val sourcesPattern = Regex("""(?:sources|file|streamUrl)\s*[:=]\s*["'](https?://[^"']+)["']""")
            val match = sourcesPattern.find(html)
            if (match != null) {
                val streamUrl = match.groupValues[1]
                if (streamUrl.contains(".m3u8") || streamUrl.contains(".mp4")) {
                    return ScrapedStreamResult(
                        streamUrl = streamUrl,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to referer,
                            "Origin" to referer.removeSuffix("/")
                        ),
                        referer = referer
                    )
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun extractSubtitlesFromHtml(html: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        try {
            val vttRegex = Regex("""(https?://[^\s"'<>\\]+?\.(?:vtt|srt)[^\s"'<>\\]*)""")
            val matches = vttRegex.findAll(html).map { it.value }.toList()
            for (vttUrl in matches) {
                if (!vttUrl.contains("preview") && !vttUrl.contains("thumb")) {
                    tracks.add(
                        SubtitleTrack(
                            url = vttUrl,
                            label = "English",
                            lang = "en",
                            default = tracks.isEmpty()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return tracks
    }
}
