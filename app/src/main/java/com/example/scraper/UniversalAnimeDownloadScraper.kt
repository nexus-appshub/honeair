package com.example.scraper

import android.util.Base64
import android.util.Log
import com.example.download.AnimeQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DirectResolvedStream(
    val directUrl: String,
    val referer: String,
    val tracks: List<AnikotoSubtitle> = emptyList(),
    val serverName: String = "Direct",
    val audioType: String = "SUB",
    val quality: String = "1080p",
    val isDirectFile: Boolean = false
)

object UniversalAnimeDownloadScraper {
    private const val TAG = "UniversalAnimeDownloadScraper"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    // --------------------------------------------------------------------------------------------------------
    // 1. DEAN EDWARDS PACKED JS SCRIPT UNPACKER (eval(function(p,a,c,k,e,r)...))
    // --------------------------------------------------------------------------------------------------------
    fun unpackPackedScript(scriptContent: String): List<String> {
        val foundUrls = mutableListOf<String>()
        try {
            val pattern = Pattern.compile("""eval\(function\(p,a,c,k,e,[rd]\)\{.*\}\('((?:[^'\\]|\\.)*)',\s*(\d+),\s*(\d+),\s*'([^']+)'\.split\('\|'\)""")
            val matcher = pattern.matcher(scriptContent)
            while (matcher.find()) {
                val p = matcher.group(1) ?: continue
                val a = matcher.group(2)?.toIntOrNull() ?: continue
                val c = matcher.group(3)?.toIntOrNull() ?: continue
                val kStr = matcher.group(4) ?: continue
                val k = kStr.split("|")

                fun encodeBase(valNum: Int): String {
                    val prefix = if (valNum < a) "" else encodeBase(valNum / a)
                    val rem = valNum % a
                    val charVal = if (rem > 35) (rem + 29).toChar() else rem.toString(36)[0]
                    return prefix + charVal
                }

                var unp = p
                for (i in c - 1 downTo 0) {
                    if (i < k.size && k[i].isNotEmpty()) {
                        val sym = encodeBase(i)
                        unp = unp.replace(Regex("\\b" + Pattern.quote(sym) + "\\b"), k[i])
                    }
                }

                val urlPattern = Pattern.compile("""https?://[^\s"'<>]+\.(?:m3u8|mp4|ts)[^\s"'<>\\]*""", Pattern.CASE_INSENSITIVE)
                val urlMatcher = urlPattern.matcher(unp)
                while (urlMatcher.find()) {
                    foundUrls.add(urlMatcher.group().replace("\\", ""))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Packed script unpack error: ${e.message}")
        }
        return foundUrls
    }

    // --------------------------------------------------------------------------------------------------------
    // 2. HEX ESCAPE UNPACKER (\x68\x74\x74\x70 -> http)
    // --------------------------------------------------------------------------------------------------------
    fun unescapeHex(content: String): String {
        return try {
            val hexPattern = Pattern.compile("""\\x([0-9a-fA-F]{2})""")
            val matcher = hexPattern.matcher(content)
            val sb = StringBuffer()
            while (matcher.find()) {
                val hex = matcher.group(1) ?: ""
                val ch = hex.toInt(16).toChar()
                matcher.appendReplacement(sb, MatcherQuote(ch.toString()))
            }
            matcher.appendTail(sb)
            sb.toString()
        } catch (e: Exception) {
            content
        }
    }

    private fun MatcherQuote(s: String): String {
        return s.replace("\\", "\\\\").replace("$", "\\$")
    }

    // --------------------------------------------------------------------------------------------------------
    // 3. MEGAPLAY / MEGACLOUD AES-256-CBC DECRYPTION ENGINE
    // --------------------------------------------------------------------------------------------------------
    fun decryptMegaplayEnc(encStr: String): String? {
        return try {
            val keyStr = "i?LMTAx0Q6,:}50U"
            val ivStr = "W0;27ToaUpl_P%'c"

            val keyBytes = ByteArray(32)
            val keySrc = keyStr.toByteArray(Charsets.UTF_8)
            System.arraycopy(keySrc, 0, keyBytes, 0, minOf(32, keySrc.size))

            val ivBytes = ByteArray(16)
            val ivSrc = ivStr.toByteArray(Charsets.UTF_8)
            System.arraycopy(ivSrc, 0, ivBytes, 0, minOf(16, ivSrc.size))

            var b64 = encStr.replace("-", "+").replace("_", "/")
            while (b64.length % 4 != 0) b64 += "="

            val cipherText = Base64.decode(b64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decryptedBytes = cipher.doFinal(cipherText)
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)

            val json = JSONObject(jsonStr)
            var file = json.optString("file", "")
            if (file.isBlank()) file = json.optString("url", "")
            if (file.isBlank()) {
                val sourcesArr = json.optJSONArray("sources")
                if (sourcesArr != null && sourcesArr.length() > 0) {
                    file = sourcesArr.getJSONObject(0).optString("file", "")
                }
            }
            if (file.isNotBlank()) file else null
        } catch (e: Exception) {
            null
        }
    }

    // --------------------------------------------------------------------------------------------------------
    // 4. DIRECT MEDIA STREAM RESOLVER FOR EMBEDS (MegaCloud, Megaplay, VidPlay, RabbitStream, etc.)
    // --------------------------------------------------------------------------------------------------------
    suspend fun resolveDirectMediaStream(
        embedUrl: String,
        fallbackReferer: String = "https://anikoto.cz/",
        extraIds: List<String> = emptyList()
    ): DirectResolvedStream = withContext(Dispatchers.IO) {
        val clean = embedUrl.trim()
        if (clean.contains(".m3u8") || clean.contains(".mp4") || clean.contains(".ts")) {
            return@withContext DirectResolvedStream(
                directUrl = clean,
                referer = fallbackReferer,
                isDirectFile = clean.contains(".mp4")
            )
        }

        try {
            val uri = URL(clean)
            val host = uri.host.lowercase()

            // MegaCloud embed-1 / embed-2 getSources API
            if (host.contains("megacloud") || host.contains("vidstream") || host.contains("rabbitstream") || uri.path.contains("/embed-")) {
                val embedMatch = Regex("""/embed-([12])/(?:e-[12]/)?([^?]+)""").find(clean)
                if (embedMatch != null) {
                    val embedVer = embedMatch.groupValues[1]
                    val hashId = embedMatch.groupValues[2]
                    val apiUrl = "${uri.protocol}://${uri.host}/embed-$embedVer/ajax/e-1/getSources?id=${URLEncoder.encode(hashId, "UTF-8")}"

                    val req = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", DEFAULT_UA)
                        .header("Referer", clean)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json, text/javascript, */*")
                        .build()

                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val data = JSONObject(body)
                        var directFile = data.optJSONObject("sources")?.optString("file") ?: ""
                        if (directFile.isBlank()) {
                            val arr = data.optJSONArray("sources")
                            if (arr != null && arr.length() > 0) directFile = arr.getJSONObject(0).optString("file", "")
                        }
                        if (directFile.isBlank() && data.has("enc")) {
                            directFile = decryptMegaplayEnc(data.optString("enc", "")) ?: ""
                        }

                        if (directFile.isNotBlank()) {
                            directFile = directFile.replace("fetch.nexabloom.top/anime/", "megap.norami.top/")
                                .replace("fetch.nexabloom.top", "megap.norami.top")
                                .replace("megap.akirax.buzz/", "megap.norami.top/")
                                .replace("ncdn.imgnex.top/anime/", "megap.norami.top/")

                            val tracksList = mutableListOf<AnikotoSubtitle>()
                            val tracksArr = data.optJSONArray("tracks")
                            if (tracksArr != null) {
                                for (tIdx in 0 until tracksArr.length()) {
                                    val tObj = tracksArr.getJSONObject(tIdx)
                                    val tFile = tObj.optString("file", "")
                                    val tLang = tObj.optString("label", tObj.optString("lang", "en"))
                                    if (tFile.isNotBlank()) {
                                        tracksList.add(AnikotoSubtitle(url = tFile, lang = tLang, label = tLang, default = tObj.optBoolean("default", false)))
                                    }
                                }
                            }

                            return@withContext DirectResolvedStream(
                                directUrl = directFile,
                                referer = "${uri.protocol}://${uri.host}/",
                                tracks = tracksList,
                                serverName = "MegaCloud HD",
                                isDirectFile = directFile.contains(".mp4")
                            )
                        }
                    }
                }
            }

            // Fetch page HTML
            val pageReq = Request.Builder()
                .url(clean)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", fallbackReferer)
                .build()

            val pageResp = httpClient.newCall(pageReq).execute()
            if (!pageResp.isSuccessful) {
                return@withContext DirectResolvedStream(directUrl = clean, referer = fallbackReferer)
            }
            val html = pageResp.body?.string() ?: ""

            // Scan Dean Edwards packed scripts
            if (html.contains("eval(function(p,a,c,k,e,")) {
                val unpacked = unpackPackedScript(html)
                if (unpacked.isNotEmpty()) {
                    return@withContext DirectResolvedStream(directUrl = unpacked[0], referer = clean)
                }
            }

            // Unescape Hex
            if (html.contains("\\x68\\x74\\x74\\x70")) {
                val unescaped = unescapeHex(html)
                val hexM3u8 = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>\\]*""").find(unescaped)
                if (hexM3u8 != null) {
                    return@withContext DirectResolvedStream(directUrl = hexM3u8.value, referer = clean)
                }
            }

            // Direct regex match for master.m3u8 or mp4
            val directMatch = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>\\]*""").find(html)
            if (directMatch != null) {
                return@withContext DirectResolvedStream(directUrl = directMatch.value, referer = clean)
            }

        } catch (e: Exception) {
            Log.w(TAG, "resolveDirectMediaStream error for $embedUrl: ${e.message}")
        }

        DirectResolvedStream(directUrl = clean, referer = fallbackReferer)
    }

    // --------------------------------------------------------------------------------------------------------
    // 5. NEKOSTREAM / MAPPER KIWI HIGH-SPEED DOWNLOAD RESOLVER
    // --------------------------------------------------------------------------------------------------------
    suspend fun fetchKiwiDownloadMirrors(malId: String, slug: String, timestamp: String): List<DirectResolvedStream> = withContext(Dispatchers.IO) {
        val mirrors = mutableListOf<DirectResolvedStream>()
        if (malId.isBlank() || slug.isBlank() || timestamp.isBlank()) return@withContext mirrors

        try {
            val url = "https://mapper.nekostream.site/api/mal/$malId/$slug/$timestamp"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "https://anikoto.cz/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val data = JSONObject(body)
                val kiwi = data.optJSONObject("Kiwi")
                val sub = kiwi?.optJSONObject("sub")
                val downloadObj = sub?.optJSONObject("download")

                if (downloadObj != null) {
                    val keys = downloadObj.keys()
                    while (keys.hasNext()) {
                        val quality = keys.next()
                        val dlUrl = downloadObj.optString(quality, "")
                        if (dlUrl.isNotBlank() && dlUrl.startsWith("http")) {
                            mirrors.add(
                                DirectResolvedStream(
                                    directUrl = dlUrl,
                                    referer = "https://anikoto.cz/",
                                    serverName = "Kiwi Direct ($quality)",
                                    quality = quality,
                                    isDirectFile = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kiwi mirror fetch error: ${e.message}")
        }
        mirrors
    }

    // --------------------------------------------------------------------------------------------------------
    // 6. MULTI-SOURCE DOWNLOAD RESOLVER (Aggregates Anikoto, Kiwi, Aniwatch, MegaCloud, VidSrc, Vidnest, Vidrock)
    // --------------------------------------------------------------------------------------------------------
    suspend fun resolveAllDownloadableOptions(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        preferDub: Boolean = false
    ): List<AnimeQualityOption> = withContext(Dispatchers.IO) {
        val options = mutableListOf<AnimeQualityOption>()
        Log.d(TAG, "Initiating universal anime download scraping for: '$title' (S${season}E${episode}, Dub: $preferDub)")

        // 1. Fetch available servers from Anikoto API
        val serverGroup = try {
            AnikotoScraper.fetchAvailableServers(title = title, season = season, episode = episode)
        } catch (e: Exception) {
            null
        }

        val targetServers = if (preferDub && serverGroup?.dubServers?.isNotEmpty() == true) {
            serverGroup.dubServers
        } else if (serverGroup?.subServers?.isNotEmpty() == true) {
            serverGroup.subServers
        } else {
            emptyList()
        }

        val resolvedDirectStreams = mutableListOf<DirectResolvedStream>()

        // Concurrently resolve embed servers
        val serverJobs = targetServers.take(4).map { srv ->
            async {
                try {
                    val embedUrl = AnikotoScraper.extractServerEmbedUrl(srv.linkId, serverGroup?.watchUrl ?: "https://anikoto.cz/")
                    if (!embedUrl.isNullOrBlank()) {
                        val resolved = resolveDirectMediaStream(embedUrl, serverGroup?.watchUrl ?: "https://anikoto.cz/")
                        if (resolved.directUrl.isNotBlank()) {
                            return@async resolved.copy(serverName = srv.name, audioType = srv.type.uppercase())
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Server resolution failure for ${srv.name}: ${e.message}")
                }
                null
            }
        }

        val resolvedServers = serverJobs.awaitAll().filterNotNull()
        resolvedDirectStreams.addAll(resolvedServers)

        // 2. Extract variants / qualities from resolved streams
        for (st in resolvedDirectStreams) {
            if (st.isDirectFile || st.directUrl.endsWith(".mp4")) {
                // Direct high-speed MP4 download file
                options.add(
                    AnimeQualityOption(
                        resolution = if (st.quality.isNotBlank()) st.quality else "1080p",
                        title = "${if (st.quality.isNotBlank()) st.quality else "1080p"} MP4 Direct",
                        badge = "DIRECT FILE (ULTRA FAST)",
                        estimatedSize = "~240 MB",
                        streamUrl = st.directUrl,
                        headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to st.referer),
                        referer = st.referer,
                        serverName = st.serverName,
                        subtitles = st.tracks
                    )
                )
            } else if (st.directUrl.contains(".m3u8")) {
                val streamRes = AnikotoStreamResult(
                    streamUrl = st.directUrl,
                    headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to st.referer),
                    referer = st.referer,
                    subtitles = st.tracks,
                    serverName = st.serverName
                )
                val parsed = parseHlsMasterQualities(streamRes)
                if (parsed.isNotEmpty()) {
                    options.addAll(parsed)
                } else {
                    options.add(
                        AnimeQualityOption(
                            resolution = "720p",
                            title = "720p HD",
                            badge = "HIGH SPEED STREAM",
                            estimatedSize = "~180 MB",
                            streamUrl = st.directUrl,
                            headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to st.referer),
                            referer = st.referer,
                            serverName = st.serverName,
                            subtitles = st.tracks
                        )
                    )
                }
            }
        }

        // 3. Fallback to standard Anikoto getStreamByTitle if still empty
        if (options.isEmpty()) {
            try {
                val fallbackStream = AnikotoScraper.getStreamByTitle(title, season, episode, preferDub)
                if (fallbackStream != null && fallbackStream.streamUrl.isNotBlank()) {
                    val streamRes = AnikotoStreamResult(
                        streamUrl = fallbackStream.streamUrl,
                        headers = fallbackStream.headers,
                        referer = fallbackStream.referer,
                        subtitles = fallbackStream.subtitles.map {
                            AnikotoSubtitle(it.url, it.lang, it.label, it.default)
                        }
                    )
                    val parsed = parseHlsMasterQualities(streamRes)
                    if (parsed.isNotEmpty()) {
                        options.addAll(parsed)
                    } else {
                        options.add(
                            AnimeQualityOption(
                                resolution = "720p",
                                title = "720p HD",
                                badge = "STANDARD HD",
                                estimatedSize = "~180 MB",
                                streamUrl = fallbackStream.streamUrl,
                                headers = fallbackStream.headers,
                                referer = fallbackStream.referer,
                                serverName = "Direct Stream",
                                subtitles = streamRes.subtitles
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback scraping error: ${e.message}")
            }
        }

        // Sort qualities from highest resolution to lowest (1080p -> 720p -> 480p -> 360p)
        options.distinctBy { it.resolution }.sortedByDescending {
            when {
                it.resolution.contains("1080") -> 1080
                it.resolution.contains("720") -> 720
                it.resolution.contains("480") -> 480
                it.resolution.contains("360") -> 360
                else -> 0
            }
        }
    }

    private fun parseHlsMasterQualities(streamRes: AnikotoStreamResult): List<AnimeQualityOption> {
        val masterUrl = streamRes.streamUrl
        val options = mutableListOf<AnimeQualityOption>()
        try {
            val req = Request.Builder()
                .url(masterUrl)
                .header("User-Agent", streamRes.headers["User-Agent"] ?: DEFAULT_UA)
                .header("Referer", streamRes.referer)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val content = resp.body?.string() ?: return emptyList()

            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var currentRes = ""
            var currentBw = 0L

            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val res = Regex("RESOLUTION=(\\d+x\\d+)").find(line)?.groupValues?.get(1) ?: run {
                        when {
                            bw > 3500000 -> "1080p"
                            bw > 1800000 -> "720p"
                            bw > 800000 -> "480p"
                            else -> "360p"
                        }
                    }
                    currentRes = res
                    currentBw = bw
                } else if (!line.startsWith("#") && currentRes.isNotEmpty()) {
                    val variantUrl = resolveAbsoluteUrl(masterUrl, line)
                    val label = when {
                        currentRes.contains("1080") || currentRes.contains("1920") -> "1080p"
                        currentRes.contains("720") || currentRes.contains("1280") -> "720p"
                        currentRes.contains("480") || currentRes.contains("854") -> "480p"
                        currentRes.contains("360") || currentRes.contains("640") -> "360p"
                        else -> currentRes
                    }
                    val estSize = when (label) {
                        "1080p" -> "~320 MB"
                        "720p" -> "~180 MB"
                        "480p" -> "~95 MB"
                        "360p" -> "~50 MB"
                        else -> "~150 MB"
                    }
                    val badge = when (label) {
                        "1080p" -> "BEST QUALITY"
                        "720p" -> "RECOMMENDED"
                        "480p" -> "DATA SAVER"
                        "360p" -> "SMALLEST SIZE"
                        else -> "STANDARD"
                    }

                    options.add(
                        AnimeQualityOption(
                            resolution = label,
                            title = "$label HD",
                            badge = badge,
                            estimatedSize = estSize,
                            streamUrl = variantUrl,
                            headers = streamRes.headers,
                            referer = streamRes.referer,
                            serverName = streamRes.serverName.ifEmpty { "Fast Server" },
                            subtitles = streamRes.subtitles
                        )
                    )
                    currentRes = ""
                    currentBw = 0L
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseHlsMasterQualities error: ${e.message}")
        }
        return options
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val base = URL(baseUrl)
                URL(base, relativeUrl).toString()
            }
        } catch (e: Exception) {
            relativeUrl
        }
    }
}
