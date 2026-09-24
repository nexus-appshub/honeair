package com.example.scraper

import android.util.Base64
import android.util.Log
import com.example.download.AnimeQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
    val tracks: List<SubtitleTrack> = emptyList(),
    val serverName: String = "Direct",
    val audioType: String = "SUB",
    val quality: String = "1080p",
    val isDirectFile: Boolean = false,
    val estimatedSize: String = "~180 MB"
)

object UniversalAnimeDownloadScraper {
    private const val TAG = "UniversalAnimeDownloadScraper"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    val httpClient: OkHttpClient by lazy {
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

                val urlPattern = Pattern.compile("""https?://[^\s"'<>]+\.(?:m3u8|mp4|ts|png\?mod=\d+|webm)[^\s"'<>\\]*""", Pattern.CASE_INSENSITIVE)
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
    // 4. DIRECT MEDIA STREAM RESOLVER FOR EMBEDS (MegaCloud, Megaplay, Vidstream, RabbitStream, etc.)
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
            // Check if URL is Megaplay trustWatch handshake
            if (clean.contains("megaplay.buzz")) {
                val idMatch = Regex("""id=([^&]+)""").find(clean)?.groupValues?.get(1)
                    ?: clean.substringAfterLast("/").substringBefore("?")
                val res = AnikotoScraper.performMegaplayHandshake(idMatch)
                if (res != null && res.streamUrl.isNotBlank()) {
                    return@withContext DirectResolvedStream(
                        directUrl = res.streamUrl,
                        referer = res.referer,
                        tracks = res.subtitles,
                        serverName = "Megaplay Stream",
                        isDirectFile = res.streamUrl.contains(".mp4")
                    )
                }
            }

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

                        if (directFile.isBlank() && data.has("encrypted") && data.optBoolean("encrypted")) {
                            val encData = data.optString("sources", "")
                            val dec = decryptMegaplayEnc(encData)
                            if (dec != null) directFile = dec
                        }

                        if (directFile.isNotBlank()) {
                            val subTracks = mutableListOf<SubtitleTrack>()
                            val tracksArr = data.optJSONArray("tracks")
                            if (tracksArr != null) {
                                for (t in 0 until tracksArr.length()) {
                                    val tObj = tracksArr.getJSONObject(t)
                                    val f = tObj.optString("file")
                                    val l = tObj.optString("label", "English")
                                    val d = tObj.optBoolean("default", false)
                                    if (f.isNotBlank() && f.contains(".vtt")) {
                                        subTracks.add(SubtitleTrack(url = f, lang = l.take(2).lowercase(), label = l, default = d))
                                    }
                                }
                            }

                            return@withContext DirectResolvedStream(
                                directUrl = directFile,
                                referer = clean,
                                tracks = if (subTracks.isNotEmpty()) subTracks else AnikotoScraper.buildFullSubtitleTracks("178939"),
                                serverName = "MegaCloud HD",
                                isDirectFile = directFile.contains(".mp4")
                            )
                        }
                    }
                }
            }

            // Fallback: fetch HTML and search for m3u8 / mp4 or packed script
            val req = Request.Builder()
                .url(clean)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", fallbackReferer)
                .header("Accept", "*/*")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val unescaped = unescapeHex(html)

                val m3u8Regex = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4|ts|png\?mod=\d+)[^\s"'<>\\]*""")
                val found = m3u8Regex.findAll(unescaped).map { it.value }.firstOrNull()

                if (found != null) {
                    return@withContext DirectResolvedStream(
                        directUrl = found,
                        referer = clean,
                        isDirectFile = found.contains(".mp4")
                    )
                }

                val unpackedUrls = unpackPackedScript(html)
                if (unpackedUrls.isNotEmpty()) {
                    return@withContext DirectResolvedStream(
                        directUrl = unpackedUrls.first(),
                        referer = clean,
                        isDirectFile = unpackedUrls.first().contains(".mp4")
                    )
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "resolveDirectMediaStream error for $embedUrl: ${e.message}")
        }

        DirectResolvedStream(directUrl = clean, referer = fallbackReferer)
    }

    // --------------------------------------------------------------------------------------------------------
    // 5. REVERSE-ENGINEERED KIWI / PAHE / KWIK DIRECT DOWNLOAD RESOLVER
    // Pipeline: https://pahe.nekostream.site/ -> https://woencalmy.cfd/ -> https://kwik.cx/f/{id}
    // Extracts direct .mp4 video stream with anti-debugger loop bypass
    // --------------------------------------------------------------------------------------------------------
    suspend fun resolveKwikDirectDownload(portalOrKwikUrl: String): DirectResolvedStream? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = portalOrKwikUrl.trim()
            Log.d(TAG, "Resolving Kwik Direct Download for: $currentUrl")

            // 1. Traverse intermediate redirects (Pahe -> Woencalmy -> Kwik)
            var targetKwikUrl = currentUrl
            if (currentUrl.contains("pahe.nekostream.site") || currentUrl.contains("woencalmy.cfd")) {
                val stepReq = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "https://anikoto.cz/")
                    .build()

                httpClient.newCall(stepReq).execute().use { resp ->
                    val respUrl = resp.request.url.toString()
                    val body = resp.body?.string() ?: ""
                    if (respUrl.contains("kwik.cx")) {
                        targetKwikUrl = respUrl
                    } else {
                        val kwikMatch = Regex("""https?://kwik\.cx/f/[a-zA-Z0-9]+""").find(body)
                        if (kwikMatch != null) {
                            targetKwikUrl = kwikMatch.value
                        }
                    }
                }
            }

            if (!targetKwikUrl.contains("kwik.cx")) {
                targetKwikUrl = currentUrl
            }

            // 2. Fetch Kwik.cx page and extract direct download form / script
            val kwikReq = Request.Builder()
                .url(targetKwikUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "https://pahe.nekostream.site/")
                .build()

            var directMp4Url = ""
            var token = ""
            var actionUrl = ""
            var resolution = "720p"
            var fileSize = "~180 MB"

            httpClient.newCall(kwikReq).execute().use { kwikResp ->
                val html = kwikResp.body?.string() ?: ""

                val titleMatch = Regex("""(?i)AnimePahe_.*?_(\d+p)_""").find(html)
                if (titleMatch != null) {
                    resolution = titleMatch.groupValues[1]
                }

                val sizeMatch = Regex("""\((\d+(?:\.\d+)?\s*(?:MB|GB))\)""").find(html)
                if (sizeMatch != null) {
                    fileSize = sizeMatch.groupValues[1]
                }

                val formActionMatch = Regex("""<form\s+action=["']([^"']+)["']\s+method=["']POST["']""").find(html)
                if (formActionMatch != null) {
                    actionUrl = formActionMatch.groupValues[1]
                }

                val tokenMatch = Regex("""<input\s+type=["']hidden["']\s+name=["']_token["']\s+value=["']([^"']+)["']""").find(html)
                if (tokenMatch != null) {
                    token = tokenMatch.groupValues[1]
                }

                val unpacked = unpackPackedScript(html)
                for (u in unpacked) {
                    if (u.contains(".mp4")) {
                        directMp4Url = u
                        break
                    }
                }

                if (directMp4Url.isBlank() && actionUrl.isNotBlank() && token.isNotBlank()) {
                    val postReq = Request.Builder()
                        .url(actionUrl)
                        .post(FormBody.Builder().add("_token", token).build())
                        .header("User-Agent", DEFAULT_UA)
                        .header("Referer", targetKwikUrl)
                        .build()

                    httpClient.newCall(postReq).execute().use { postResp ->
                        val loc = postResp.header("Location")
                        if (!loc.isNullOrBlank()) {
                            directMp4Url = loc
                        }
                    }
                }
            }

            if (directMp4Url.isNotBlank()) {
                Log.d(TAG, "Successfully extracted Kwik Direct MP4 ($resolution - $fileSize): $directMp4Url")
                return@withContext DirectResolvedStream(
                    directUrl = directMp4Url,
                    referer = targetKwikUrl,
                    serverName = "Kiwi Direct ($resolution)",
                    quality = resolution,
                    isDirectFile = true,
                    estimatedSize = fileSize
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveKwikDirectDownload error: ${e.message}")
        }
        null
    }

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
                            val kwikDirect = resolveKwikDirectDownload(dlUrl)
                            if (kwikDirect != null) {
                                mirrors.add(kwikDirect)
                            } else {
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
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kiwi mirror fetch error: ${e.message}")
        }
        mirrors
    }

    // --------------------------------------------------------------------------------------------------------
    // 6. NATIVE IN-APP ANIME STREAM EXTRACTION ENGINE
    // --------------------------------------------------------------------------------------------------------
    suspend fun extractNativeAnimeStream(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        preferDub: Boolean = false
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val serverGroup = AnikotoScraper.fetchAvailableServers(title = title, season = season, episode = episode)
            val preferredList = if (preferDub) serverGroup.dubServers.ifEmpty { serverGroup.subServers } else serverGroup.subServers.ifEmpty { serverGroup.dubServers }

            for (srv in preferredList) {
                if (srv.streamUrl.isNotBlank() && srv.streamUrl.contains(".m3u8")) {
                    return@withContext ScrapedStreamResult(
                        streamUrl = srv.streamUrl,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to srv.referer.ifBlank { "https://anikoto.cz/" },
                            "Origin" to "https://anikoto.cz"
                        ),
                        referer = srv.referer.ifBlank { "https://anikoto.cz/" },
                        subtitles = srv.tracks
                    )
                }

                val embedUrl = AnikotoScraper.extractServerEmbedUrl(srv.linkId, serverGroup.watchUrl.ifBlank { "https://anikoto.cz/" })
                if (!embedUrl.isNullOrBlank()) {
                    val resolved = resolveDirectMediaStream(embedUrl, serverGroup.watchUrl)
                    if (resolved.directUrl.isNotBlank()) {
                        return@withContext ScrapedStreamResult(
                            streamUrl = resolved.directUrl,
                            headers = mapOf(
                                "User-Agent" to DEFAULT_UA,
                                "Referer" to resolved.referer,
                                "Origin" to "https://anikoto.cz"
                            ),
                            referer = resolved.referer,
                            subtitles = resolved.tracks
                        )
                    }
                }
            }

            // Fallback direct title resolution
            val fallbackStream = AnikotoScraper.getStreamByTitle(title, season, episode, preferDub)
            if (fallbackStream != null && fallbackStream.streamUrl.isNotBlank()) {
                return@withContext ScrapedStreamResult(
                    streamUrl = fallbackStream.streamUrl,
                    headers = fallbackStream.headers,
                    referer = fallbackStream.referer,
                    subtitles = fallbackStream.subtitles
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractNativeAnimeStream error: ${e.message}")
        }
        null
    }

    // --------------------------------------------------------------------------------------------------------
    // 7. COMPREHENSIVE ALL-QUALITY DOWNLOAD RESOLVER
    // --------------------------------------------------------------------------------------------------------
    suspend fun resolveAllDownloadableOptions(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        preferDub: Boolean = false
    ): List<AnimeQualityOption> = withContext(Dispatchers.IO) {
        val allQualities = mutableListOf<AnimeQualityOption>()

        // 1. Check Kiwi direct downloads first
        try {
            val serverGroup = AnikotoScraper.fetchAvailableServers(title = title, season = season, episode = episode)
            val kiwiServers = (serverGroup.subServers + serverGroup.dubServers).filter { it.name.contains("Kiwi", ignoreCase = true) || it.linkId.contains("pahe") }

            for (ks in kiwiServers) {
                val direct = resolveKwikDirectDownload(ks.linkId)
                if (direct != null && direct.directUrl.isNotBlank()) {
                    allQualities.add(
                        AnimeQualityOption(
                            resolution = direct.quality,
                            title = "${direct.quality} Ultra Direct",
                            badge = "DIRECT MP4",
                            estimatedSize = direct.estimatedSize,
                            streamUrl = direct.directUrl,
                            headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to direct.referer),
                            referer = direct.referer,
                            serverName = "Kiwi Direct",
                            subtitles = ks.tracks.map { AnikotoSubtitle(it.url, it.lang, it.label, it.default) }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kiwi download extraction error: ${e.message}")
        }

        // 2. Stream playlist HLS variant parsing
        if (allQualities.isEmpty()) {
            val streamResult = extractNativeAnimeStream(title, season, episode, preferDub)
            if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                val parsedQualities = parseHlsMasterQualities(streamResult)
                if (parsedQualities.isNotEmpty()) {
                    allQualities.addAll(parsedQualities)
                } else {
                    allQualities.add(
                        AnimeQualityOption(
                            resolution = "720p",
                            title = "720p HD",
                            badge = "RECOMMENDED",
                            estimatedSize = "~180 MB",
                            streamUrl = streamResult.streamUrl,
                            headers = streamResult.headers,
                            referer = streamResult.referer,
                            serverName = "Fast Stream",
                            subtitles = streamResult.subtitles.map { AnikotoSubtitle(it.url, it.lang, it.label, it.default) }
                        )
                    )
                }
            }
        }

        allQualities.distinctBy { it.resolution }.sortedByDescending {
            when {
                it.resolution.contains("1080") -> 1080
                it.resolution.contains("720") -> 720
                it.resolution.contains("480") -> 480
                it.resolution.contains("360") -> 360
                else -> 0
            }
        }
    }

    private fun parseHlsMasterQualities(streamRes: ScrapedStreamResult): List<AnimeQualityOption> {
        val masterUrl = streamRes.streamUrl
        val options = mutableListOf<AnimeQualityOption>()
        try {
            val req = Request.Builder().url(masterUrl)
            req.header("User-Agent", streamRes.headers["User-Agent"] ?: DEFAULT_UA)
            req.header("Referer", streamRes.referer)
            req.header("Origin", "https://anikoto.cz")
            val resp = httpClient.newCall(req.build()).execute()
            val content = resp.body?.string() ?: ""
            if (content.isBlank()) return emptyList()

            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var currentRes = ""

            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val res = Regex("RESOLUTION=(\\d+x\\d+)").find(line)?.groupValues?.get(1) ?: "1280x720"
                    currentRes = res
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

                    options.add(
                        AnimeQualityOption(
                            resolution = label,
                            title = "$label HD",
                            badge = if (label == "1080p") "BEST QUALITY" else "RECOMMENDED",
                            estimatedSize = estSize,
                            streamUrl = variantUrl,
                            headers = streamRes.headers,
                            referer = streamRes.referer,
                            serverName = "Fast Stream",
                            subtitles = streamRes.subtitles.map { AnikotoSubtitle(it.url, it.lang, it.label, it.default) }
                        )
                    )
                    currentRes = ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseHlsMasterQualities error: ${e.message}")
        }
        return options
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            relativeUrl
        } else {
            val baseWithoutQuery = baseUrl.substringBefore("?")
            val lastSlash = baseWithoutQuery.lastIndexOf('/')
            val parentPath = if (lastSlash > 0) baseWithoutQuery.substring(0, lastSlash + 1) else "$baseWithoutQuery/"
            parentPath + relativeUrl
        }
    }
}
