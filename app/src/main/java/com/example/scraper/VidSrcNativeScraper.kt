package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VidSrcNativeScraper {
    private const val TAG = "VidSrcNativeScraper"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val VIDSRC_HOSTS = listOf(
        "https://vidsrc.me",
        "https://vidsrc.to",
        "https://vidsrc.xyz",
        "https://vidsrc.vip",
        "https://vidsrc.cc",
        "https://vidsrc.in",
        "https://vidsrc.pm",
        "https://vidsrc.net",
        "https://vidsrc.rip",
        "https://vidsrc.pro"
    )

    suspend fun extractStream(
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val cleanTmdb = tmdbId.removePrefix("movie_").removePrefix("series_").removePrefix("anikoto_").trim()
        val effectiveImdb = if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) imdbId.trim() else null

        // Try vidsrc.me / vidsrc.to / vidsrc.xyz embed endpoints
        for (host in VIDSRC_HOSTS) {
            try {
                val candidateUrls = mutableListOf<String>()

                if (isTv) {
                    if (!effectiveImdb.isNullOrBlank()) {
                        candidateUrls.add("$host/embed/tv?imdb=$effectiveImdb&season=$season&episode=$episode")
                        candidateUrls.add("$host/embed/tv/$effectiveImdb/$season/$episode")
                    }
                    if (cleanTmdb.all { it.isDigit() }) {
                        candidateUrls.add("$host/embed/tv?tmdb=$cleanTmdb&season=$season&episode=$episode")
                        candidateUrls.add("$host/embed/tv/$cleanTmdb/$season/$episode")
                        candidateUrls.add("$host/embed/$cleanTmdb/$season/$episode")
                    }
                } else {
                    if (!effectiveImdb.isNullOrBlank()) {
                        candidateUrls.add("$host/embed/movie?imdb=$effectiveImdb")
                        candidateUrls.add("$host/embed/movie/$effectiveImdb")
                        candidateUrls.add("$host/embed/$effectiveImdb")
                    }
                    if (cleanTmdb.all { it.isDigit() }) {
                        candidateUrls.add("$host/embed/movie?tmdb=$cleanTmdb")
                        candidateUrls.add("$host/embed/movie/$cleanTmdb")
                        candidateUrls.add("$host/embed/$cleanTmdb")
                    }
                }

                for (embedUrl in candidateUrls) {
                    try {
                        val req = Request.Builder()
                            .url(embedUrl)
                            .header("User-Agent", DEFAULT_UA)
                            .header("Referer", "$host/")
                            .header("Origin", host)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .build()

                        val resp = httpClient.newCall(req).execute()
                        val html = resp.body?.string() ?: ""
                        if (resp.isSuccessful && html.isNotBlank()) {
                            // 1. Direct stream search in HTML
                            val directStream = extractDirectStreamFromHtml(html, host)
                            if (directStream != null) {
                                Log.d(TAG, "VidSrc direct stream found from $embedUrl: ${directStream.streamUrl}")
                                return@withContext directStream
                            }

                            // 2. Extract rcp or iframe hash
                            val rcpUrl = extractRcpUrl(html, host)
                            if (!rcpUrl.isNullOrBlank()) {
                                val rcpReq = Request.Builder()
                                    .url(rcpUrl)
                                    .header("User-Agent", DEFAULT_UA)
                                    .header("Referer", embedUrl)
                                    .header("Origin", host)
                                    .build()

                                val rcpResp = httpClient.newCall(rcpReq).execute()
                                val rcpHtml = rcpResp.body?.string() ?: ""
                                if (rcpResp.isSuccessful && rcpHtml.isNotBlank()) {
                                    val rcpStream = extractDirectStreamFromHtml(rcpHtml, host)
                                    if (rcpStream != null) {
                                        Log.d(TAG, "VidSrc RCP stream found: ${rcpStream.streamUrl}")
                                        return@withContext rcpStream
                                    }

                                    // Check prourl / src4 / cloudflare intermediate sources
                                    val proStream = extractProUrlStream(rcpHtml, rcpUrl, host)
                                    if (proStream != null) {
                                        Log.d(TAG, "VidSrc ProURL stream found: ${proStream.streamUrl}")
                                        return@withContext proStream
                                    }
                                }
                            }

                            // 3. Extract sub-iframes inside the embed
                            val iframeUrl = extractIframeUrl(html, host)
                            if (!iframeUrl.isNullOrBlank() && iframeUrl != rcpUrl) {
                                val ifReq = Request.Builder()
                                    .url(iframeUrl)
                                    .header("User-Agent", DEFAULT_UA)
                                    .header("Referer", embedUrl)
                                    .build()
                                val ifResp = httpClient.newCall(ifReq).execute()
                                val ifHtml = ifResp.body?.string() ?: ""
                                if (ifResp.isSuccessful && ifHtml.isNotBlank()) {
                                    val ifStream = extractDirectStreamFromHtml(ifHtml, host)
                                    if (ifStream != null) {
                                        return@withContext ifStream
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "VidSrc candidate $embedUrl failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "VidSrc host $host failed: ${e.message}")
            }
        }
        null
    }

    private fun extractRcpUrl(html: String, host: String): String? {
        val rcpPatterns = listOf(
            Regex("""(?:src|data-src)=["']([^"']*?/rcp/[^"']*)["']"""),
            Regex("""(?:src|data-src)=["']([^"']*?/prourl/[^"']*)["']"""),
            Regex("""(?:src|data-src)=["']([^"']*?/src4/[^"']*)["']"""),
            Regex("""["'](https?://[^"']*?/rcp/[^"']*)["']""")
        )

        for (pattern in rcpPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                var url = match.groupValues[1]
                if (url.startsWith("//")) {
                    url = "https:$url"
                } else if (url.startsWith("/")) {
                    url = "$host$url"
                }
                return url
            }
        }
        return null
    }

    private fun extractIframeUrl(html: String, host: String): String? {
        val iframePattern = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val match = iframePattern.find(html)
        if (match != null) {
            var url = match.groupValues[1]
            if (url.startsWith("//")) {
                url = "https:$url"
            } else if (url.startsWith("/")) {
                url = "$host$url"
            }
            if (!url.contains("about:blank") && !url.contains("ads") && !url.contains("sandbox")) {
                return url
            }
        }
        return null
    }

    private fun extractProUrlStream(html: String, referer: String, host: String): ScrapedStreamResult? {
        // Search for encoded player source or hls endpoint
        val hlsPattern = Regex("""(https?://[^\s"'<>\\]+?\.(?:m3u8|mp4)[^\s"'<>\\]*)""")
        val match = hlsPattern.find(html)
        if (match != null) {
            val streamUrl = match.groupValues[1]
            return ScrapedStreamResult(
                streamUrl = streamUrl,
                headers = mapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to referer,
                    "Origin" to host
                ),
                referer = referer
            )
        }
        return null
    }

    private fun extractDirectStreamFromHtml(html: String, host: String): ScrapedStreamResult? {
        val m3u8Regex = Regex("""https?://[^\s"'<>\\]+?\.(?:m3u8|mp4)[^\s"'<>\\]*""")
        val matches = m3u8Regex.findAll(html).map { it.value }.toList()
        for (url in matches) {
            if (!url.contains("analytics") && !url.contains("favicon") && !url.contains("preview") && !url.contains("sample")) {
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
