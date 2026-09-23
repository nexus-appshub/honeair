package com.example.download

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.scraper.AnikotoScraper
import com.example.scraper.AnikotoServer
import com.example.scraper.AnikotoStreamResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AnimeQualityOption(
    val resolution: String,
    val title: String,
    val badge: String,
    val estimatedSize: String,
    val streamUrl: String,
    val headers: Map<String, String>,
    val referer: String,
    val serverName: String,
    val subtitles: List<com.example.scraper.AnikotoSubtitle> = emptyList()
)

data class AnimeEpisodeDownloadInfo(
    val animeTitle: String,
    val season: Int,
    val episode: Int,
    val isDub: Boolean,
    val serverName: String,
    val qualities: List<AnimeQualityOption>
)

object AnimeDownloader {
    private const val TAG = "AnimeDownloader"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Resolves all available download qualities and subtitles for an anime episode by doing live scraping.
     */
    suspend fun resolveAnimeDownloadOptions(
        context: Context,
        title: String,
        season: Int = 1,
        episode: Int = 1,
        preferDub: Boolean = false
    ): AnimeEpisodeDownloadInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resolving anime download streams for '$title' (S${season}E${episode}, Dub: $preferDub)")

        val allQualities = mutableListOf<AnimeQualityOption>()
        var resolvedServerName = if (preferDub) "DUB Fast Stream" else "SUB Fast Stream"

        // Tier 1: Deep Universal Anime Download Scraper (Dean Edwards Unpacker + MegaCloud / Megaplay AES Decrypt + Kiwi Mirrors)
        try {
            val deepScrapedQualities = com.example.scraper.UniversalAnimeDownloadScraper.resolveAllDownloadableOptions(
                title = title,
                season = season,
                episode = episode,
                preferDub = preferDub
            )
            if (deepScrapedQualities.isNotEmpty()) {
                allQualities.addAll(deepScrapedQualities)
                resolvedServerName = deepScrapedQualities.firstOrNull()?.serverName ?: resolvedServerName
            }
        } catch (e: Exception) {
            Log.e(TAG, "UniversalAnimeDownloadScraper error: ${e.message}")
        }

        // Tier 2: Anikoto live server group fallback
        if (allQualities.isEmpty()) {
            val serverGroup = try {
                AnikotoScraper.fetchAvailableServers(title = title, season = season, episode = episode)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching anime servers: ${e.message}")
                null
            }

            val targetServers = if (preferDub && serverGroup?.dubServers?.isNotEmpty() == true) {
                serverGroup.dubServers
            } else if (serverGroup?.subServers?.isNotEmpty() == true) {
                serverGroup.subServers
            } else {
                emptyList()
            }

            for (srv in targetServers) {
                try {
                    val embedUrl = AnikotoScraper.extractServerEmbedUrl(srv.linkId, serverGroup?.watchUrl ?: "https://anikoto.cz/")
                    if (!embedUrl.isNullOrBlank()) {
                        val streamRes = AnikotoScraper.extractM3u8AndSubtitlesFromEmbed(embedUrl)
                        if (streamRes != null && streamRes.streamUrl.isNotBlank()) {
                            resolvedServerName = srv.name
                            val parsedQualities = parseHlsMasterQualities(streamRes)
                            if (parsedQualities.isNotEmpty()) {
                                allQualities.addAll(parsedQualities)
                                break
                            } else {
                                allQualities.add(
                                    AnimeQualityOption(
                                        resolution = "720p",
                                        title = "720p HD",
                                        badge = "DEFAULT QUALITY",
                                        estimatedSize = "~180 MB",
                                        streamUrl = streamRes.streamUrl,
                                        headers = streamRes.headers,
                                        referer = streamRes.referer,
                                        serverName = srv.name,
                                        subtitles = streamRes.subtitles
                                    )
                                )
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Server ${srv.name} resolution error: ${e.message}")
                }
            }
        }

        // Tier 3: Universal getStreamByTitle fallback
        if (allQualities.isEmpty()) {
            try {
                val fallbackStream = AnikotoScraper.getStreamByTitle(title, season, episode, preferDub)
                if (fallbackStream != null && fallbackStream.streamUrl.isNotBlank()) {
                    val streamRes = AnikotoStreamResult(
                        streamUrl = fallbackStream.streamUrl,
                        headers = fallbackStream.headers,
                        referer = fallbackStream.referer,
                        subtitles = fallbackStream.subtitles.map {
                            com.example.scraper.AnikotoSubtitle(it.url, it.lang, it.label, it.default)
                        }
                    )
                    val parsed = parseHlsMasterQualities(streamRes)
                    if (parsed.isNotEmpty()) {
                        allQualities.addAll(parsed)
                    } else {
                        allQualities.add(
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
                Log.e(TAG, "Fallback stream error: ${e.message}")
            }
        }

        // Tier 4: UnifiedStreamManager concurrent multi-scraper engine
        if (allQualities.isEmpty()) {
            try {
                Log.d(TAG, "Fallback Tier 4: Querying UnifiedStreamManager for $title (S$season Ep$episode)...")
                val unifiedStream = kotlinx.coroutines.runBlocking {
                    com.example.scraper.UnifiedStreamManager.getStream(
                        context = context,
                        title = title,
                        tmdbId = "anikoto_${title.replace(" ", "-").lowercase()}",
                        isTv = true,
                        season = season,
                        episode = episode,
                        isAnime = true
                    )
                }
                if (unifiedStream != null && unifiedStream.streamUrl.isNotBlank()) {
                    val streamRes = AnikotoStreamResult(
                        streamUrl = unifiedStream.streamUrl,
                        headers = unifiedStream.headers,
                        referer = unifiedStream.referer,
                        subtitles = emptyList()
                    )
                    val parsed = parseHlsMasterQualities(streamRes)
                    if (parsed.isNotEmpty()) {
                        allQualities.addAll(parsed)
                    } else {
                        allQualities.add(
                            AnimeQualityOption(
                                resolution = "720p",
                                title = "720p HD",
                                badge = "UNIFIED HD",
                                estimatedSize = "~180 MB",
                                streamUrl = unifiedStream.streamUrl,
                                headers = unifiedStream.headers,
                                referer = unifiedStream.referer,
                                serverName = "Unified Engine",
                                subtitles = emptyList()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UnifiedStreamManager fallback error: ${e.message}")
            }
        }

        // Format and sort qualities cleanly (1080p -> 720p -> 480p -> 360p)
        val sortedQualities = allQualities.distinctBy { it.resolution }.sortedByDescending {
            when {
                it.resolution.contains("1080") -> 1080
                it.resolution.contains("720") -> 720
                it.resolution.contains("480") -> 480
                it.resolution.contains("360") -> 360
                else -> 0
            }
        }

        AnimeEpisodeDownloadInfo(
            animeTitle = title,
            season = season,
            episode = episode,
            isDub = preferDub,
            serverName = resolvedServerName,
            qualities = sortedQualities
        )
    }

    /**
     * Parses all variant qualities from an HLS master playlist with correct headers
     */
    private fun parseHlsMasterQualities(streamRes: AnikotoStreamResult): List<AnimeQualityOption> {
        val masterUrl = streamRes.streamUrl
        val options = mutableListOf<AnimeQualityOption>()
        try {
            val content = fetchTextWithHeaders(masterUrl, streamRes.headers, streamRes.referer)
            if (content.isBlank()) return emptyList()

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

    /**
     * Extracts direct downloadable URL if available, or returns formatted link.
     */
    fun getDirectDownloadUrl(streamUrl: String, quality: String = "720p"): String? {
        return if (streamUrl.contains(".mp4") || streamUrl.contains("kwik.cx") || streamUrl.contains("pahe.nekostream.site") || streamUrl.contains("woencalmy.cfd")) {
            streamUrl
        } else if (streamUrl.startsWith("http")) {
            streamUrl
        } else {
            null
        }
    }

    /**
     * Starts direct MP4 download without HLS segment processing.
     */
    fun startNativeDirectDownload(
        context: Context,
        animeTitle: String,
        season: Int = 1,
        episode: Int = 1,
        quality: String = "720p",
        directUrl: String,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        val sanitizedTitle = animeTitle.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
        val fileName = "${sanitizedTitle}_S${season}E${episode}_${quality}.mp4"
        MediaDownloader.downloadFile(
            context = context,
            url = directUrl,
            fileName = fileName,
            coroutineScope = coroutineScope,
            userAgent = DEFAULT_UA,
            referer = "https://anikoto.cz/"
        )
    }

    /**
     * Main Anime Download Entry Point.
     * Dispatches the download to DownloadService or directly executes with full notification tracking.
     */
    fun startAnimeDownload(
        context: Context,
        animeTitle: String,
        season: Int = 1,
        episode: Int = 1,
        qualityOption: AnimeQualityOption,
        coroutineScope: CoroutineScope
    ) {
        val sanitizedTitle = animeTitle.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
        val fileName = "${sanitizedTitle}_S${season}E${episode}_${qualityOption.resolution}.mp4"

        // Also download subtitle if available
        val defaultSub = qualityOption.subtitles.find { it.default || it.lang.equals("en", ignoreCase = true) }
            ?: qualityOption.subtitles.firstOrNull()

        if (defaultSub != null && defaultSub.url.startsWith("http")) {
            coroutineScope.launch(Dispatchers.IO) {
                downloadCompanionSubtitle(context, defaultSub.url, "${sanitizedTitle}_S${season}E${episode}.vtt", qualityOption.referer)
            }
        }

        // Start background download through MediaDownloader & DownloadService pipeline
        MediaDownloader.downloadFile(
            context = context,
            url = qualityOption.streamUrl,
            fileName = fileName,
            coroutineScope = coroutineScope,
            userAgent = qualityOption.headers["User-Agent"] ?: DEFAULT_UA,
            referer = qualityOption.referer
        )
    }

    /**
     * Executes robust anime HLS stream download with AES-128 decryption and anti-403 retry engine.
     */
    fun downloadAnimeHlsStream(
        context: Context,
        playlistUrl: String,
        fileName: String,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        downloadId: String,
        customHeaders: Map<String, String>? = null,
        referer: String? = null
    ): File? {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val cleanOutName = if (fileName.endsWith(".m3u8")) fileName.replace(".m3u8", ".mp4") else fileName
        var targetFile = File(downloadsDir, cleanOutName)
        var idx = 1
        val baseName = cleanOutName.substringBeforeLast(".")
        val extension = cleanOutName.substringAfterLast(".", "mp4")
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "$baseName($idx).$extension")
            idx++
        }

        Log.d(TAG, "Starting Anime Stream Download -> Target: ${targetFile.name} (URL: $playlistUrl)")

        val effectiveHeaders = (customHeaders ?: emptyMap()).toMutableMap()
        if (!effectiveHeaders.containsKey("User-Agent")) effectiveHeaders["User-Agent"] = DEFAULT_UA
        if (!effectiveHeaders.containsKey("Accept")) effectiveHeaders["Accept"] = "*/*"
        if (!effectiveHeaders.containsKey("Origin")) effectiveHeaders["Origin"] = "https://megaplay.buzz"
        val effectiveReferer = referer ?: "https://anikoto.cz/"

        // 1. Fetch Playlist Content
        var currentPlaylistUrl = playlistUrl
        var playlistText = fetchTextWithHeaders(currentPlaylistUrl, effectiveHeaders, effectiveReferer)

        // If it's a master playlist, select the best variant stream
        if (playlistText.contains("#EXT-X-STREAM-INF")) {
            val lines = playlistText.lines().map { it.trim() }
            var variantLine: String? = null
            for (idxLine in lines.indices) {
                if (lines[idxLine].startsWith("#EXT-X-STREAM-INF")) {
                    for (nextIdx in idxLine + 1 until lines.size) {
                        val candidate = lines[nextIdx]
                        if (candidate.isNotBlank() && !candidate.startsWith("#")) {
                            variantLine = candidate
                            break
                        }
                    }
                    if (variantLine != null) break
                }
            }
            if (variantLine != null) {
                currentPlaylistUrl = resolveAbsoluteUrl(currentPlaylistUrl, variantLine)
                playlistText = fetchTextWithHeaders(currentPlaylistUrl, effectiveHeaders, effectiveReferer)
            }
        }

        val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val segmentUrls = mutableListOf<String>()
        var keyUrl: String? = null
        var keyIvBytes: ByteArray? = null
        var isAesEncrypted = false

        for (line in lines) {
            if (line.startsWith("#EXT-X-KEY")) {
                if (line.contains("METHOD=AES-128")) {
                    isAesEncrypted = true
                    val uriMatch = Regex("""URI=["']([^"']+)["']""").find(line)
                    if (uriMatch != null) {
                        keyUrl = resolveAbsoluteUrl(currentPlaylistUrl, uriMatch.groupValues[1])
                    }
                    val ivMatch = Regex("""IV=0x([0-9a-fA-F]+)""").find(line)
                    if (ivMatch != null) {
                        val hex = ivMatch.groupValues[1]
                        keyIvBytes = hexStringToByteArray(hex)
                    }
                }
            } else if (!line.startsWith("#") && line.isNotBlank()) {
                segmentUrls.add(resolveAbsoluteUrl(currentPlaylistUrl, line))
            }
        }

        if (segmentUrls.isEmpty()) {
            throw Exception("No anime video segments found in playlist.")
        }

        // Fetch AES-128 Key if needed
        var aesKeyBytes: ByteArray? = null
        if (isAesEncrypted && !keyUrl.isNullOrBlank()) {
            Log.d(TAG, "Fetching AES-128 Decryption Key from: $keyUrl")
            aesKeyBytes = fetchBinaryWithHeaders(keyUrl, effectiveHeaders, effectiveReferer)
            if (aesKeyBytes == null || aesKeyBytes.size != 16) {
                Log.w(TAG, "AES-128 key length is invalid (${aesKeyBytes?.size ?: 0} bytes), proceeding with raw chunks")
            } else {
                Log.d(TAG, "AES-128 key fetched successfully (16 bytes)")
            }
        }

        val outputStream = FileOutputStream(targetFile)
        val totalSegments = segmentUrls.size
        var downloadedSegments = 0
        var totalBytesDownloadedSoFar = 0L
        var lastUpdateMs = 0L

        try {
            for (i in 0 until totalSegments) {
                MediaDownloader.checkCancellationAndPause(downloadId)
                val segUrl = segmentUrls[i]
                var segBytes: ByteArray? = null
                var retries = 4

                while (segBytes == null && retries > 0) {
                    MediaDownloader.checkCancellationAndPause(downloadId)
                    try {
                        segBytes = fetchBinaryWithHeaders(segUrl, effectiveHeaders, effectiveReferer)
                        if (segBytes == null || segBytes.isEmpty()) {
                            retries--
                            Thread.sleep(400)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        retries--
                        Thread.sleep(600)
                    }
                }

                if (segBytes == null || segBytes.isEmpty()) {
                    throw Exception("Failed to download anime video segment $i after retries.")
                }

                // Decrypt segment if AES-128 encrypted
                val finalBytes = if (aesKeyBytes != null && aesKeyBytes.size == 16) {
                    try {
                        val iv = keyIvBytes ?: generateSequenceIv(i)
                        decryptAes128(segBytes, aesKeyBytes, iv)
                    } catch (e: Exception) {
                        Log.w(TAG, "Decryption error for segment $i: ${e.message}, using raw")
                        segBytes
                    }
                } else {
                    segBytes
                }

                outputStream.write(finalBytes)
                totalBytesDownloadedSoFar += finalBytes.size
                downloadedSegments++

                val currentMs = System.currentTimeMillis()
                if (currentMs - lastUpdateMs > 300 || downloadedSegments == totalSegments) {
                    val percent = ((downloadedSegments * 100) / totalSegments).coerceIn(0, 100)
                    val estTotal = if (downloadedSegments > 0) {
                        (totalBytesDownloadedSoFar * totalSegments) / downloadedSegments
                    } else -1L
                    MediaDownloader.updateNotificationProgress(
                        context = context,
                        downloadId = downloadId,
                        percent = percent,
                        downloadedBytes = totalBytesDownloadedSoFar,
                        totalBytes = estTotal,
                        builder = builder,
                        notificationManager = notificationManager,
                        notificationId = notificationId
                    )
                    lastUpdateMs = currentMs
                }
            }
        } finally {
            try {
                outputStream.flush()
                outputStream.close()
            } catch (_: Exception) {}
        }

        if (targetFile.exists() && targetFile.length() < 15_000) {
            val len = targetFile.length()
            targetFile.delete()
            throw Exception("Downloaded anime video is too small ($len bytes). Server stream may have expired.")
        }

        Log.d(TAG, "Anime download finished: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
        return targetFile
    }

    private fun fetchTextWithHeaders(url: String, customHeaders: Map<String, String>, referer: String): String {
        val requests = buildCandidateRequests(url, customHeaders, referer)
        for (req in requests) {
            try {
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    if (body.isNotBlank()) return body
                }
                resp.close()
            } catch (_: Exception) {}
        }
        return ""
    }

    private fun fetchBinaryWithHeaders(url: String, customHeaders: Map<String, String>, referer: String): ByteArray? {
        val requests = buildCandidateRequests(url, customHeaders, referer)
        for (req in requests) {
            try {
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    resp.close()
                    if (bytes != null && bytes.isNotEmpty()) return bytes
                }
                resp.close()
            } catch (_: Exception) {}
        }
        return null
    }

    private fun buildCandidateRequests(url: String, customHeaders: Map<String, String>, referer: String): List<Request> {
        val list = mutableListOf<Request>()
        val uri = try { Uri.parse(url) } catch (_: Exception) { null }
        val host = uri?.host ?: "anikoto.cz"
        val origin = "${uri?.scheme ?: "https"}://$host"

        val systemCookies = try {
            android.webkit.CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }

        // 1. Primary request with custom headers & host origin
        val b1 = Request.Builder().url(url)
        b1.header("User-Agent", customHeaders["User-Agent"] ?: DEFAULT_UA)
        b1.header("Accept", "*/*")
        b1.header("Referer", referer.ifEmpty { "$origin/" })
        b1.header("Origin", origin)
        if (!systemCookies.isNullOrBlank()) {
            b1.header("Cookie", systemCookies)
        }
        for ((k, v) in customHeaders) {
            if (k !in listOf("User-Agent", "Accept", "Referer", "Origin", "Cookie")) {
                b1.header(k, v)
            }
        }
        list.add(b1.build())

        // 2. Direct host Referer
        val b2 = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_UA)
            .header("Accept", "*/*")
            .header("Referer", "$origin/")
            .header("Origin", origin)
        if (!systemCookies.isNullOrBlank()) {
            b2.header("Cookie", systemCookies)
        }
        list.add(b2.build())

        // 3. Megacloud / Anikoto fallback Referer
        val b3 = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_UA)
            .header("Accept", "*/*")
            .header("Referer", "https://anikoto.cz/")
        if (!systemCookies.isNullOrBlank()) {
            b3.header("Cookie", systemCookies)
        }
        list.add(b3.build())

        return list
    }

    private fun downloadCompanionSubtitle(context: Context, subUrl: String, fileName: String, referer: String) {
        try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val subFile = File(downloadsDir, fileName)
            val content = fetchTextWithHeaders(subUrl, mapOf("User-Agent" to DEFAULT_UA), referer)
            if (content.isNotBlank()) {
                subFile.writeText(content)
                Log.d(TAG, "Companion subtitle saved: ${subFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading companion subtitle: ${e.message}")
        }
    }

    private fun decryptAes128(cipherText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(cipherText)
    }

    private fun generateSequenceIv(sequenceNumber: Int): ByteArray {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(0)
        buffer.putLong(sequenceNumber.toLong())
        return buffer.array()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val clean = if (s.startsWith("0x", ignoreCase = true)) s.substring(2) else s
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        val normalizedRelative = relativeUrl.replace("\\", "/")
        val cleanRelative = if (normalizedRelative.startsWith("./")) normalizedRelative.substring(2) else normalizedRelative
        if (cleanRelative.startsWith("http://") || cleanRelative.startsWith("https://")) {
            return cleanRelative
        }
        return try {
            val baseUri = Uri.parse(baseUrl)
            if (cleanRelative.startsWith("/")) {
                "${baseUri.scheme}://${baseUri.authority}$cleanRelative"
            } else {
                val path = baseUri.path ?: ""
                val lastSlash = path.lastIndexOf('/')
                val basePath = if (lastSlash >= 0) path.substring(0, lastSlash + 1) else "/"
                "${baseUri.scheme}://${baseUri.authority}$basePath$cleanRelative"
            }
        } catch (_: Exception) {
            cleanRelative
        }
    }
}
