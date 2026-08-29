package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VidrockNativeScraper {
    private const val TAG = "VidrockNativeScraper"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val BASE_HOSTS = listOf(
        "https://vidrock.ru",
        "https://vidsrc.xyz",
        "https://vidsrc.cc",
        "https://vidsrc.me"
    )

    /**
     * Extracts direct HLS Master / Index .m3u8 stream link based on VidRock & VidSrc architecture.
     */
    suspend fun extractStream(
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        if (tmdbId.isBlank()) return@withContext null

        val typePath = if (isTv) "tv/$tmdbId/$season/$episode" else "movie/$tmdbId"

        // 1. Try Direct VidRock embed & v-endpoint
        for (host in BASE_HOSTS) {
            try {
                val embedUrl = "$host/embed/$typePath"
                Log.d(TAG, "Attempting VidRock embed extraction: $embedUrl")

                val request = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "$host/")
                    .header("Origin", host)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val response = httpClient.newCall(request).execute()
                val html = response.body?.string() ?: ""

                if (response.isSuccessful && html.isNotBlank()) {
                    val stream = extractM3u8FromHtml(html, host, tmdbId)
                    if (stream != null && stream.streamUrl.isNotBlank()) {
                        Log.d(TAG, "Successfully extracted VidRock stream from $host: ${stream.streamUrl}")
                        return@withContext stream
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "VidRock host $host extraction failed: ${e.message}")
            }
        }

        // 2. Try Direct Content Endpoint: GET https://vidrock.ru/v/{tmdb_id}
        try {
            val vEndpoint = "https://vidrock.ru/v/$tmdbId"
            val vReq = Request.Builder()
                .url(vEndpoint)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "https://vidrock.ru/")
                .header("Origin", "https://vidrock.ru")
                .header("Accept", "text/html,application/json,*/*")
                .build()

            val vResp = httpClient.newCall(vReq).execute()
            val vBody = vResp.body?.string() ?: ""
            if (vResp.isSuccessful && vBody.isNotBlank()) {
                val stream = extractM3u8FromHtml(vBody, "https://vidrock.ru", tmdbId)
                if (stream != null && stream.streamUrl.isNotBlank()) {
                    Log.d(TAG, "Successfully extracted VidRock stream from /v/ endpoint: ${stream.streamUrl}")
                    return@withContext stream
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VidRock /v/ endpoint failed: ${e.message}")
        }

        // 3. Try Dynamic TMDB-named intermediate proxy & HLS CDN endpoints
        // Architecture: https://{tmdbId}.xyz / https://www.dolphin-cf.{tmdbId}.xyz / https://ch.tsload7.com
        val dynamicProxyHosts = listOf(
            "https://www.dolphin-cf.$tmdbId.xyz",
            "https://$tmdbId.xyz",
            "https://ch.tsload7.com",
            "https://gtg.og-114.tsload7.com"
        )

        for (proxyHost in dynamicProxyHosts) {
            try {
                val masterUrl = "$proxyHost/hls/$tmdbId/master.m3u8"
                val checkReq = Request.Builder()
                    .url(masterUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "https://vidrock.ru/")
                    .header("Origin", "https://vidrock.ru")
                    .build()

                val checkResp = httpClient.newCall(checkReq).execute()
                if (checkResp.isSuccessful) {
                    val body = checkResp.body?.string() ?: ""
                    if (body.contains("#EXTM3U") || body.contains("#EXT-X-STREAM-INF") || body.contains(".m3u8") || body.contains(".ts")) {
                        Log.d(TAG, "Found working VidRock CDN Master M3U8 at $masterUrl")
                        return@withContext buildVidrockResult(masterUrl, tmdbId)
                    }
                }
            } catch (_: Exception) {}
        }

        null
    }

    private fun extractM3u8FromHtml(html: String, host: String, tmdbId: String): ScrapedStreamResult? {
        // Pattern 1: Direct .m3u8 URLs in HTML/JS (master.m3u8, index.m3u8, etc.)
        val m3u8Regex = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").matcher(html)
        if (m3u8Regex.find()) {
            val url = m3u8Regex.group(1)
            if (!url.isNullOrBlank()) {
                val cleanUrl = url.replace("\\/", "/")
                return buildVidrockResult(cleanUrl, tmdbId)
            }
        }

        // Pattern 2: iframe / source src extraction
        val iframeRegex = Pattern.compile("""(?:iframe|source)\s+[^>]*src=["']([^"']+)["']""").matcher(html)
        while (iframeRegex.find()) {
            val src = iframeRegex.group(1) ?: continue
            val fixedSrc = if (src.startsWith("//")) "https:$src" else if (src.startsWith("/")) "$host$src" else src
            if (fixedSrc.contains(".m3u8")) {
                return buildVidrockResult(fixedSrc.replace("\\/", "/"), tmdbId)
            }
            if (fixedSrc.contains("vidrock") || fixedSrc.contains("vidsrc") || fixedSrc.contains("tsload") || fixedSrc.contains("xyz")) {
                try {
                    val subReq = Request.Builder()
                        .url(fixedSrc)
                        .header("User-Agent", DEFAULT_UA)
                        .header("Referer", "$host/")
                        .header("Origin", host)
                        .build()
                    val subResp = httpClient.newCall(subReq).execute()
                    val subHtml = subResp.body?.string() ?: ""
                    val subMatcher = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").matcher(subHtml)
                    if (subMatcher.find()) {
                        val foundUrl = subMatcher.group(1)?.replace("\\/", "/")
                        if (!foundUrl.isNullOrBlank()) {
                            return buildVidrockResult(foundUrl, tmdbId)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Pattern 3: JSON embedded config (file: "...", url: "...", sources: [...])
        val jsonPattern = Pattern.compile("""(?:file|url|src|source)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").matcher(html)
        if (jsonPattern.find()) {
            val url = jsonPattern.group(1)
            if (!url.isNullOrBlank()) {
                return buildVidrockResult(url.replace("\\/", "/"), tmdbId)
            }
        }

        return null
    }

    private fun buildVidrockResult(streamUrl: String, tmdbId: String): ScrapedStreamResult {
        val headers = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to "https://vidrock.ru/",
            "Origin" to "https://vidrock.ru",
            "Accept" to "*/*"
        )
        return ScrapedStreamResult(
            streamUrl = streamUrl,
            headers = headers,
            referer = "https://vidrock.ru/"
        )
    }
}
