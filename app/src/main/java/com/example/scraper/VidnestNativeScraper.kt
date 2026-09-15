package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class VidnestProviderInfo(
    val key: String,
    val displayName: String,
    val pathSegment: String,
    val defaultReferer: String = "https://vidnest.fun/"
)

object VidnestNativeScraper {
    private const val TAG = "VidnestNativeScraper"
    private const val ALPHABET = "RB0fpH8ZEyVLkv7c2i6MAJ5u3IKFDxlS1NTsnGaqmXYdUrtzjwObCgQP94hoeW+/="
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

    val PROVIDERS = listOf(
        VidnestProviderInfo("delta", "HINDI", "allmovies", "https://vidnest.fun/"),
        VidnestProviderInfo("filxer", "Filxer (Fast)", "rogflix", "https://rogflix.fun/"),
        VidnestProviderInfo("lamda", "Lamda (Ultra)", "allmovies", "https://vidnest.fun/"),
        VidnestProviderInfo("prime", "Prime (VidRock)", "vidrock", "https://vidrock.net/"),
        VidnestProviderInfo("hexa", "Hexa (VidLink)", "vidlink", "https://vidlink.pro/"),
        VidnestProviderInfo("zeta", "Zeta (NextGen)", "nextgencloudfabric", "https://nextgencloudfabric.com/"),
        VidnestProviderInfo("alfa", "Alfa (Videasy)", "videasy", "https://tiktoks.animanga.fun/"),
        VidnestProviderInfo("gama", "Gama (VidZee)", "vidzee", "https://s1.streamflixapi.site/"),
        VidnestProviderInfo("ophim", "Ophim (HD)", "klikxxi", "https://vidnest.fun/"),
        VidnestProviderInfo("catflix", "Catflix (Buzz)", "buzz", "https://ployan.me/"),
        VidnestProviderInfo("beta", "Beta (VidXYZ)", "vidxyz", "https://moviesapi.to/"),
        VidnestProviderInfo("sigma", "Sigma (Holly)", "hollymoviehd", "https://vidnest.fun/")
    )

    private val BASE_URLS = listOf(
        "https://new.vidnest.fun",
        "https://vidnest.fun",
        "https://vidnest.xyz",
        "https://api.vidnest.fun"
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Decodes custom Base64 encrypted cipher returned by Vidnest servers.
     */
    fun decryptCipher(dataStr: String): String {
        try {
            val trimmed = dataStr.trim().trim('"', '\'')
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return trimmed
            }

            val charMap = IntArray(256) { 64 }
            for (i in ALPHABET.indices) {
                charMap[ALPHABET[i].code] = i
            }

            val bytesOut = java.io.ByteArrayOutputStream()
            val len = trimmed.length
            var t = 0
            while (t < len) {
                val end = minOf(t + 4, len)
                var chunk = trimmed.substring(t, end)
                while (chunk.length < 4) {
                    chunk += "="
                }

                val c0 = chunk[0].code; val l0 = if(c0 < 256) charMap[c0] else 64
                val c1 = chunk[1].code; val l1 = if(c1 < 256) charMap[c1] else 64
                val c2 = chunk[2].code; val l2 = if(c2 < 256) charMap[c2] else 64
                val c3 = chunk[3].code; val l3 = if(c3 < 256) charMap[c3] else 64

                val b0 = ((l0 shl 2) or (l1 shr 4)) and 0xFF
                bytesOut.write(b0)

                if (l2 != 64) {
                    val b1 = (((l1 and 15) shl 4) or (l2 shr 2)) and 0xFF
                    bytesOut.write(b1)
                }

                if (l3 != 64) {
                    val b2 = (((l2 and 3) shl 6) or l3) and 0xFF
                    bytesOut.write(b2)
                }

                t += 4
            }

            return String(bytesOut.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt Vidnest cipher: ${e.message}")
            return ""
        }
    }

    private fun sanitizeTmdbId(rawId: String): String {
        return rawId.trim()
            .removePrefix("movie_")
            .removePrefix("series_")
            .removePrefix("anikoto_")
            .trim()
    }

    suspend fun resolveToNumericTmdbId(rawId: String, isTv: Boolean): String = withContext(Dispatchers.IO) {
        val clean = sanitizeTmdbId(rawId)
        if (clean.all { it.isDigit() }) {
            return@withContext clean
        }
        if (clean.startsWith("tt")) {
            try {
                val findRes = com.example.data.network.RetrofitClient.tmdbApi.getByExternalId(clean)
                val resolved = if (isTv) {
                    findRes.tv_results?.firstOrNull()?.id?.toString()
                        ?: findRes.movie_results?.firstOrNull()?.id?.toString()
                } else {
                    findRes.movie_results?.firstOrNull()?.id?.toString()
                        ?: findRes.tv_results?.firstOrNull()?.id?.toString()
                }
                if (!resolved.isNullOrBlank()) {
                    return@withContext resolved
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving IMDB $clean to TMDB: ${e.message}")
            }
        }
        clean
    }

    suspend fun extractStreamFromProvider(
        providerKey: String,
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val provider = PROVIDERS.find { it.key == providerKey } ?: PROVIDERS.first()
        val numericId = resolveToNumericTmdbId(tmdbId, isTv)
        val typeSegment = if (isTv) "tv" else "movie"
        val querySuffix = if (isTv) "$numericId/$season/$episode" else numericId

        // Direct special endpoints for key providers like Filxer/Rogflix
        if (provider.key == "filxer") {
            val directRogflixUrls = listOf(
                "https://rogflix.fun/api/stream/$typeSegment/$querySuffix",
                "https://rogflix.fun/$typeSegment/$querySuffix"
            )
            for (directUrl in directRogflixUrls) {
                try {
                    val req = Request.Builder()
                        .url(directUrl)
                        .header("User-Agent", DEFAULT_UA)
                        .header("Referer", "https://rogflix.fun/")
                        .header("Origin", "https://rogflix.fun")
                        .header("Accept", "application/json, text/plain, */*")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val bStr = resp.body?.string() ?: ""
                    if (resp.isSuccessful && bStr.isNotBlank()) {
                        val parsed = parseStreamPayload(bStr, provider)
                        if (parsed != null && parsed.streamUrl.isNotBlank()) {
                            Log.d(TAG, "Direct Filxer/Rogflix stream extracted: ${parsed.streamUrl}")
                            return@withContext parsed
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        for (baseUrl in BASE_URLS) {
            val endpointUrl = "$baseUrl/${provider.pathSegment}/$typeSegment/$querySuffix"
            try {
                val request = Request.Builder()
                    .url(endpointUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "https://vidnest.fun/")
                    .header("Origin", "https://vidnest.fun")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    var decryptedPayload = bodyStr
                    if (bodyStr.contains("\"data\"")) {
                        try {
                            val rootJson = JSONObject(bodyStr)
                            val encData = rootJson.optString("data", "")
                            if (encData.isNotBlank()) {
                                val decrypted = decryptCipher(encData)
                                if (decrypted.isNotBlank()) {
                                    decryptedPayload = decrypted
                                }
                            }
                        } catch (_: Exception) {}
                    } else if (!bodyStr.trim().startsWith("{") && !bodyStr.trim().startsWith("[")) {
                        val decrypted = decryptCipher(bodyStr)
                        if (decrypted.isNotBlank()) {
                            decryptedPayload = decrypted
                        }
                    }

                    if (decryptedPayload.isNotBlank()) {
                        val streamResult = parseStreamPayload(decryptedPayload, provider)
                        if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                            Log.d(TAG, "Successfully extracted Vidnest stream from [${provider.displayName}]: ${streamResult.streamUrl}")
                            return@withContext streamResult
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vidnest provider [${provider.displayName}] error on $endpointUrl: ${e.message}")
            }
        }
        null
    }

    /**
     * HIGH POWER CONCURRENT SCRAPER:
     * Scrapes all VidNest sub-providers simultaneously in parallel.
     * Evaluates and returns the best and fastest extracted stream link, prioritizing Filxer.
     */
    suspend fun extractStream(
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val numericId = resolveToNumericTmdbId(tmdbId, isTv)
        
        // Fast-path: Check Filxer (Fast) first as it is the most reliable
        try {
            val filxerStream = extractStreamFromProvider("filxer", numericId, isTv, season, episode)
            if (filxerStream != null && filxerStream.streamUrl.isNotBlank()) {
                Log.d(TAG, "Filxer (Fast) priority hit: ${filxerStream.streamUrl}")
                return@withContext filxerStream
            }
        } catch (_: Exception) {}

        coroutineScope {
            Log.d(TAG, "Starting Concurrent High-Power Fast Scraping across ${PROVIDERS.size} VidNest sub-providers for TMDB: $numericId")
            val channel = kotlinx.coroutines.channels.Channel<ScrapedStreamResult>(PROVIDERS.size)
            
            // Launch all sub-server providers concurrently
            val jobs = PROVIDERS.map { provider ->
                launch(Dispatchers.IO) {
                    try {
                        val result = extractStreamFromProvider(provider.key, numericId, isTv, season, episode)
                        if (result != null && result.streamUrl.isNotBlank()) {
                            channel.trySend(result)
                        }
                    } catch (_: Exception) {}
                }
            }

            var best: ScrapedStreamResult? = null
            try {
                best = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                    channel.receive()
                }
            } catch (_: Exception) {}

            jobs.forEach { it.cancel() }
            if (best != null) {
                Log.d(TAG, "Winning VidNest provider resolved direct stream: ${best.streamUrl}")
                return@coroutineScope best
            }
            null
        }
    }

    /**
     * Deep Scraping Mode: Thoroughly tries all providers sequentially and concurrently with retry
     */
    suspend fun extractStreamDeep(
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val numericId = resolveToNumericTmdbId(tmdbId, isTv)
        Log.d(TAG, "Deep Scraping Mode initiated for TMDB: $numericId (isTv: $isTv, S:$season E:$episode)")

        // 1. Fast parallel check with Filxer priority
        val fastResult = extractStream(numericId, isTv, season, episode)
        if (fastResult != null && fastResult.streamUrl.isNotBlank()) {
            return@withContext fastResult
        }

        // 2. Deep Sequential Exhaustive Scan across all providers
        for (provider in PROVIDERS) {
            try {
                val res = extractStreamFromProvider(provider.key, numericId, isTv, season, episode)
                if (res != null && res.streamUrl.isNotBlank()) {
                    Log.d(TAG, "Deep Scraping resolved stream via [${provider.displayName}]: ${res.streamUrl}")
                    return@withContext res
                }
            } catch (_: Exception) {}
        }
        null
    }

    /**
     * Extract all working streams mapped by provider key for multi-server picker.
     */
    suspend fun extractAllWorkingStreams(
        tmdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): Map<String, ScrapedStreamResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            val resultMap = ConcurrentHashMap<String, ScrapedStreamResult>()
            val deferredTasks = PROVIDERS.map { provider ->
                async(Dispatchers.IO) {
                    val res = extractStreamFromProvider(provider.key, tmdbId, isTv, season, episode)
                    if (res != null && res.streamUrl.isNotBlank()) {
                        resultMap[provider.key] = res
                    }
                }
            }
            deferredTasks.awaitAll()
            resultMap
        }
    }

    private fun parseStreamPayload(payload: String, provider: VidnestProviderInfo): ScrapedStreamResult? {
        try {
            val json = JSONObject(payload)
            var extractedUrl = ""
            val headersMap = mutableMapOf<String, String>()

            // 1. Direct "url" field (e.g. alfa, catflix, filxer, zeta)
            if (json.has("url") && json.optString("url").isNotBlank()) {
                extractedUrl = json.optString("url")
            }

            // 2. Nested "data" -> "stream" -> "playlist" (e.g. hexa / vidlink)
            if (extractedUrl.isBlank() && json.has("data")) {
                val dataObj = json.optJSONObject("data")
                val streamObj = dataObj?.optJSONObject("stream")
                val playlist = streamObj?.optString("playlist", "") ?: ""
                if (playlist.isNotBlank()) {
                    extractedUrl = playlist
                }
            }

            // 3. "sources" array (e.g. prime, ophim, catflix all_urls)
            if (extractedUrl.isBlank() && json.has("sources")) {
                val sourcesArr = json.optJSONArray("sources")
                if (sourcesArr != null && sourcesArr.length() > 0) {
                    for (i in 0 until sourcesArr.length()) {
                        val srcObj = sourcesArr.optJSONObject(i) ?: continue
                        val url = srcObj.optString("url", "")
                        if (url.isNotBlank()) {
                            extractedUrl = url
                            val srcHeaders = srcObj.optJSONObject("headers")
                            if (srcHeaders != null) {
                                val keys = srcHeaders.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    headersMap[k] = srcHeaders.optString(k)
                                }
                            }
                            break
                        }
                    }
                }
            }

            // 4. "streams" array (e.g. gama, beta, sigma, lamda, delta)
            if (extractedUrl.isBlank() && json.has("streams")) {
                val streamsArr = json.optJSONArray("streams")
                if (streamsArr != null && streamsArr.length() > 0) {
                    for (i in 0 until streamsArr.length()) {
                        val streamObj = streamsArr.optJSONObject(i) ?: continue
                        val url = streamObj.optString("url", "")
                        if (url.isNotBlank()) {
                            extractedUrl = url
                            val streamHeaders = streamObj.optJSONObject("headers")
                            if (streamHeaders != null) {
                                val keys = streamHeaders.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    headersMap[key] = streamHeaders.optString(key)
                                }
                            }
                            break
                        }
                    }
                }
            }

            // 5. "all_urls" array fallback
            if (extractedUrl.isBlank() && json.has("all_urls")) {
                val allUrlsArr = json.optJSONArray("all_urls")
                if (allUrlsArr != null && allUrlsArr.length() > 0) {
                    for (i in 0 until allUrlsArr.length()) {
                        val u = allUrlsArr.optString(i, "")
                        if (u.isNotBlank()) {
                            extractedUrl = u
                            break
                        }
                    }
                }
            }

            if (extractedUrl.isBlank()) return null

            // Clean URL formatting
            extractedUrl = extractedUrl.trim().replace("\\/", "/")

            // Parse root headers if present
            if (json.has("headers")) {
                val rootHeaders = json.optJSONObject("headers")
                if (rootHeaders != null) {
                    val keys = rootHeaders.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        headersMap[key] = rootHeaders.optString(key)
                    }
                }
            }

            // Ensure essential headers exist for playback
            if (!headersMap.containsKey("User-Agent")) {
                headersMap["User-Agent"] = DEFAULT_UA
            }
            if (!headersMap.containsKey("Referer")) {
                headersMap["Referer"] = provider.defaultReferer
            }
            if (!headersMap.containsKey("Origin")) {
                headersMap["Origin"] = "https://vidnest.fun"
            }

            val referer = headersMap["Referer"] ?: provider.defaultReferer

            // Extract subtitle tracks from payload if available
            val subtitlesList = mutableListOf<SubtitleTrack>()
            val tracks = json.optJSONArray("tracks")
                ?: json.optJSONArray("subtitles")
                ?: json.optJSONArray("captions")
                ?: json.optJSONObject("data")?.optJSONArray("tracks")
                ?: json.optJSONObject("data")?.optJSONArray("subtitles")

            if (tracks != null) {
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val file = t.optString("file", "").ifEmpty { t.optString("url", "") }
                    val label = t.optString("label", "").ifEmpty { t.optString("lang", "English") }
                    val lang = t.optString("lang", "").ifEmpty { t.optString("language", label.take(2).lowercase()) }
                    val isDefault = t.optBoolean("default", false)
                    val kind = t.optString("kind", "")
                    if (file.isNotBlank() && (kind.isEmpty() || kind == "captions" || kind == "subtitles" || file.endsWith(".vtt", ignoreCase = true) || file.endsWith(".srt", ignoreCase = true))) {
                        subtitlesList.add(
                            SubtitleTrack(
                                url = file,
                                lang = lang.ifBlank { "en" },
                                label = label.ifBlank { "English" },
                                default = isDefault
                            )
                        )
                    }
                }
            }

            return ScrapedStreamResult(
                streamUrl = extractedUrl,
                headers = headersMap,
                referer = referer,
                subtitles = subtitlesList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing stream payload: ${e.message}")
            return null
        }
    }
}
