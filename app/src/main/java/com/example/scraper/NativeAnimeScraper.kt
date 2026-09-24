package com.example.scraper

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

data class MapperServer(
    @SerializedName("server") val server: String = "",
    @SerializedName("id") val id: String = "",
    @SerializedName("token") val token: String = ""
)

data class TrustWatchResponse(
    @SerializedName("status") val status: Int = 0,
    @SerializedName("result") val result: TrustWatchResult? = null
)

data class TrustWatchResult(
    @SerializedName("sources") val sources: List<TrustWatchSource>? = null,
    @SerializedName("tracks") val tracks: List<TrustWatchTrack>? = null,
    @SerializedName("intro") val intro: TimeRange? = null,
    @SerializedName("outro") val outro: TimeRange? = null
)

data class TrustWatchSource(
    @SerializedName("file") val file: String = "",
    @SerializedName("type") val type: String = ""
)

data class TrustWatchTrack(
    @SerializedName("file") val file: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("kind") val kind: String = ""
)

data class TimeRange(
    @SerializedName("start") val start: Int? = null,
    @SerializedName("end") val end: Int? = null
)

/**
 * Small process-local CookieJar so a normal check_domain -> trustWatch
 * request sequence can reuse cookies set by the provider.
 */
private class MemoryCookieJar : CookieJar {
    private val cookies = CopyOnWriteArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeIf {
                it.name == cookie.name &&
                    it.domain == cookie.domain &&
                    it.path == cookie.path
            }
            if (!cookie.persistent || cookie.expiresAt > System.currentTimeMillis()) {
                this.cookies.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.filter { it.matches(url) }
    }
}

/**
 * Direct native provider chain:
 *
 *   Anikoto HTML
 *       -> episode data-id
 *       -> Nekostream mapper
 *       -> Megapay check_domain
 *       -> Megapay trustWatch
 *       -> HLS + VTT
 *
 * No HomeAir proxy/backend is required by this class.
 *
 * Important: this class intentionally does NOT solve CAPTCHA/Turnstile,
 * emulate anti-bot challenges, or decrypt an undocumented encrypted
 * protected response. In those cases it fails with a diagnostic instead
 * of silently falling back to another server.
 */
object NativeAnimeScraper {
    private const val TAG = "NativeAnimeScraper"

    const val ANIKOTO_BASE = "https://anikoto.cz"
    const val MAPPER_BASE = "https://mapper.nekostream.online"
    const val MEGAPAY_BASE = "https://megapay.buzz"

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 14; TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val cookieJar = MemoryCookieJar()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Volatile
    var lastError: String? = null
        private set

    suspend fun extractStream(
        titleOrSlug: String,
        season: Int = 1,
        episodeNum: Int = 1,
        audioType: String = "sub",
        requestedServerKey: String? = null
    ): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        val episode = episodeNum.coerceAtLeast(1)
        lastError = null

        try {
            val watchUrl = resolveWatchUrl(titleOrSlug, season, episode)
            if (watchUrl.isNullOrBlank()) {
                fail("Could not resolve an Anikoto watch URL for $titleOrSlug S$season Ep$episode")
            }

            val episodeHtml = getText(
                url = watchUrl,
                referer = "$ANIKOTO_BASE/",
                accept = "text/html,application/xhtml+xml"
            )

            val episodeId = extractEpisodeIdFromHtml(episodeHtml)
                ?: fail("Anikoto episode page did not expose a usable data-id")

            Log.d(TAG, "Resolved episodeId=$episodeId from $watchUrl")

            val mapperJson = getText(
                url = "$MAPPER_BASE/ep/${encodePathSegment(episodeId)}",
                referer = "$ANIKOTO_BASE/",
                accept = "application/json,*/*"
            )

            val mapperType = object : TypeToken<List<MapperServer>>() {}.type
            val mapperServers: List<MapperServer> = Gson().fromJson(mapperJson, mapperType)
                ?: emptyList()

            val sortedMapperServers = mutableListOf<MapperServer>()
            val isExplicitServer = !requestedServerKey.isNullOrBlank()

            if (isExplicitServer) {
                val matching = mapperServers.filter { matchesRequestedServer(it, requestedServerKey!!) }
                if (matching.isEmpty()) {
                    fail("Requested server '$requestedServerKey' is not offered by mapper for episodeId $episodeId")
                }
                sortedMapperServers.addAll(matching)
            } else {
                val vidstream2 = mapperServers.filter { it.server.equals("vidstream-2", true) }
                val hd1 = mapperServers.filter { it.server.equals("hd-1", true) }
                val others = mapperServers.filter { !it.server.equals("vidstream-2", true) && !it.server.equals("hd-1", true) }
                sortedMapperServers.addAll(vidstream2)
                sortedMapperServers.addAll(hd1)
                sortedMapperServers.addAll(others)
            }

            if (sortedMapperServers.isEmpty()) {
                fail("Mapper returned no servers for episodeId $episodeId")
            }

            var successResult: ScrapedStreamResult? = null
            var lastEx: Exception? = null

            for (target in sortedMapperServers) {
                try {
                    if (target.id.isBlank() || target.token.isBlank()) {
                        continue
                    }

                    val epochSeconds = System.currentTimeMillis() / 1000L

                    val domainJson = getText(
                        url = "$MEGAPAY_BASE/check_domain.json?cache_buster=$epochSeconds",
                        referer = "$ANIKOTO_BASE/",
                        accept = "application/json,*/*"
                    )

                    validateCheckDomain(domainJson)

                    val formBody = FormBody.Builder()
                        .add("id", target.id)
                        .add("token", target.token)
                        .add("v", epochSeconds.toString())
                        .build()

                    val trustWatchRequest = Request.Builder()
                        .url("$MEGAPAY_BASE/stream/trustWatch")
                        .post(formBody)
                        .header("User-Agent", DEFAULT_UA)
                        .header("Accept", "application/json,*/*")
                        .header("Origin", ANIKOTO_BASE)
                        .header("Referer", "$ANIKOTO_BASE/")
                        .build()

                    val trustWatchBody = client.newCall(trustWatchRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            fail("trustWatch HTTP ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }

                    val parsed = parseTrustWatchResponse(trustWatchBody)
                    if (parsed == null || parsed.status != 200 || parsed.result == null) {
                        continue
                    }

                    val result = parsed.result
                    val source = result.sources.orEmpty()
                        .firstOrNull { it.type.equals("hls", ignoreCase = true) }
                        ?: result.sources.orEmpty().firstOrNull { it.file.contains(".m3u8", true) }
                        ?: result.sources.orEmpty().firstOrNull()

                    if (source == null || source.file.isBlank()) {
                        continue
                    }

                    val streamUrl = source.file.trim()
                    val subtitles = result.tracks.orEmpty()
                        .filter { it.file.isNotBlank() && isSubtitleTrack(it) }
                        .distinctBy { it.file }
                        .map {
                            SubtitleTrack(
                                url = it.file.trim(),
                                lang = languageCode(it.label),
                                label = it.label.ifBlank { "Subtitle" },
                                default = it.label.contains("English", true)
                            )
                        }

                    successResult = ScrapedStreamResult(
                        streamUrl = streamUrl,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to "$MEGAPAY_BASE/",
                            "Origin" to MEGAPAY_BASE
                        ),
                        referer = "$MEGAPAY_BASE/",
                        subtitles = subtitles,
                        resolvedServerKey = target.id.ifBlank { target.server },
                        resolvedServerName = target.server,
                        audioType = audioType,
                        season = season,
                        episode = episode,
                        mediaId = titleOrSlug
                    )
                    break
                } catch (e: Exception) {
                    lastEx = e
                    Log.w(TAG, "Failed resolving mapper server ${target.server}: ${e.message}")
                }
            }

            if (successResult != null) {
                return@withContext successResult
            } else {
                throw lastEx ?: NativeScraperException("All candidate servers failed to resolve")
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Native scraping failed", e)
            return@withContext null
        }
    }

    private fun resolveWatchUrl(titleOrSlug: String, season: Int, episode: Int): String? {
        val raw = titleOrSlug.trim()
        if (raw.isBlank()) return null

        val cleaned = raw
            .removePrefix("anikoto_")
            .removePrefix("anime_")
            .trim()

        if (cleaned.startsWith("$ANIKOTO_BASE/watch/")) {
            val slug = cleaned.substringAfter("/watch/")
                .substringBefore("?")
                .trim('/')
                .substringBefore("/ep-")
            if (slug.isNotBlank()) {
                return "$ANIKOTO_BASE/watch/$slug/ep-$episode"
            }
        }

        if (Regex("^[a-z0-9]+(?:-[a-z0-9]+)+$", RegexOption.IGNORE_CASE).matches(cleaned)) {
            return "$ANIKOTO_BASE/watch/$cleaned/ep-$episode"
        }

        return resolveWatchUrlFromSearch(cleaned, season, episode)
    }

    private fun getRomanNumeral(number: Int): String {
        return when (number) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            5 -> "V"
            6 -> "VI"
            7 -> "VII"
            8 -> "VIII"
            9 -> "IX"
            10 -> "X"
            else -> number.toString()
        }
    }

    private fun matchesSeason(label: String, season: Int): Boolean {
        if (season == 1) {
            val lower = label.lowercase()
            if (lower.contains("season 2") || lower.contains("season 3") || lower.contains("season 4") || lower.contains("season 5") ||
                lower.contains(" s2") || lower.contains(" s3") || lower.contains(" s4") || lower.contains(" s5") ||
                lower.contains("part 2") || lower.contains("part 3") || lower.contains(" ii ") || lower.contains(" iii ") ||
                lower.endsWith(" ii") || lower.endsWith(" iii")) {
                return false
            }
            return true
        }
        val sNum = season.toString()
        val roman = getRomanNumeral(season).lowercase()
        val lower = label.lowercase()
        return lower.contains("season $sNum") ||
               lower.contains(" s$sNum") ||
               lower.contains("part $sNum") ||
               lower.contains(" $roman ") ||
               lower.endsWith(" $roman") ||
               lower.contains("${sNum}st season") ||
               lower.contains("${sNum}nd season") ||
               lower.contains("${sNum}rd season") ||
               lower.contains("${sNum}th season")
    }

    private fun matchesRequestedServer(mapperServer: MapperServer, requestedKey: String): Boolean {
        val msName = mapperServer.server.trim().lowercase()
        val msId = mapperServer.id.trim().lowercase()
        val rKey = requestedKey.trim().lowercase()

        if (msName.isEmpty() && msId.isEmpty()) return false
        if (msName == rKey || msId == rKey) return true

        val cleanMsName = msName.replace(" ", "-").replace("_", "-")
        val cleanRKey = rKey.replace(" ", "-").replace("_", "-")
        val cleanMsId = msId.replace(" ", "-").replace("_", "-")

        return cleanMsName == cleanRKey || cleanMsId == cleanRKey
    }

    private fun resolveWatchUrlFromSearch(
        title: String,
        season: Int,
        episode: Int
    ): String? {
        val cleanTitle = title.replace(Regex("""(?i)(?:season|part|cour|arc|s)\s*\d+.*"""), "").trim()

        val candidateQueries = mutableListOf<String>()
        if (season > 1) {
            candidateQueries.add("$cleanTitle Season $season")
            candidateQueries.add("$cleanTitle $season")
            candidateQueries.add("$cleanTitle S$season")
            candidateQueries.add("$cleanTitle Part $season")
            candidateQueries.add("$cleanTitle ${getRomanNumeral(season)}")
        }
        candidateQueries.add(cleanTitle)

        val target = normalizeTitle(cleanTitle)

        for (query in candidateQueries) {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val urls = listOf(
                "$ANIKOTO_BASE/filter?keyword=$encoded",
                "$ANIKOTO_BASE/search?keyword=$encoded"
            )

            for (url in urls) {
                try {
                    val html = getText(
                        url = url,
                        referer = "$ANIKOTO_BASE/",
                        accept = "text/html,application/xhtml+xml"
                    )
                    val doc = Jsoup.parse(html)

                    val candidates = doc
                        .select("a[href*=\"/watch/\"], .flw-item a, .film-name a")
                        .mapNotNull { element ->
                            val href = element.attr("href").trim()
                            if (!href.contains("/watch/")) return@mapNotNull null

                            val label = element.text().trim()
                                .ifBlank { element.attr("title").trim() }

                            if (label.isBlank()) return@mapNotNull null

                            val absolute = if (href.startsWith("http")) {
                                href
                            } else {
                                "$ANIKOTO_BASE${if (href.startsWith("/")) "" else "/"}$href"
                            }

                            if (!matchesSeason(label, season)) return@mapNotNull null

                            Triple(label, absolute, scoreTitle(target, normalizeTitle(label)))
                        }
                        .distinctBy { it.second }

                    val best = candidates.maxByOrNull { it.third }
                    if (best != null && best.third >= 35) {
                        val slug = best.second.substringAfter("/watch/")
                            .substringBefore("?")
                            .trim('/')
                            .substringBefore("/ep-")
                        if (slug.isNotBlank()) {
                            return "$ANIKOTO_BASE/watch/$slug/ep-$episode"
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Search resolver failed for $url: ${e.message}")
                }
            }
        }

        return null
    }

    /**
     * Pure HTML helper; kept internal so it can be unit-tested without HTTP.
     */
    internal fun extractEpisodeIdFromHtml(html: String): String? {
        val doc = Jsoup.parse(html)

        val prioritized = buildList {
            addAll(doc.select("[data-episode-id]"))
            addAll(doc.select("[data-video-id]"))
            addAll(doc.select("#video-player-container[data-id]"))
            addAll(doc.select(".video-player[data-id]"))
            addAll(doc.select("[data-id]"))
        }.distinct()

        val attrs = prioritized.flatMap { element ->
            listOf(
                element.attr("data-episode-id"),
                element.attr("data-video-id"),
                element.attr("data-id")
            )
        }.map { it.trim() }.filter { it.isNotBlank() }

        attrs.firstOrNull { it.all(Char::isDigit) }?.let { return it }
        attrs.firstOrNull()?.let { return it }

        // Last-resort HTML attribute scan for layouts that wrap the player
        // inside scripts or custom elements instead of the selectors above.
        val regex = Regex("""data-id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Pure mapper helper; chooses providers in deterministic priority order.
     */
    internal fun chooseMapperServer(
        servers: List<MapperServer>
    ): MapperServer? {
        val usable = servers.filter { it.id.isNotBlank() && it.token.isNotBlank() }
        return usable.firstOrNull { it.server.equals("vidstream-2", true) }
            ?: usable.firstOrNull { it.server.equals("hd-1", true) }
            ?: usable.firstOrNull()
    }

    /**
     * Pure trustWatch helper. Encrypted/non-JSON payloads intentionally fail.
     */
    internal fun parseTrustWatchResponse(
        json: String
    ): TrustWatchResponse? {
        return try {
            Gson().fromJson(json, TrustWatchResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun validateCheckDomain(json: String) {
        try {
            val obj = JSONObject(json)
            val ok = obj.optBoolean("success", false)
            val status = obj.optInt("status", 0)
            val domain = obj.optString("domain", "")

            if (!ok && status !in 200..299) {
                fail("Megapay check_domain rejected the session")
            }
            if (domain.isNotBlank() && !domain.equals("megapay.buzz", true)) {
                fail("Unexpected Megapay domain: $domain")
            }
        } catch (_: Exception) {
            fail("Megapay check_domain returned an invalid response")
        }
    }

    private fun getText(
        url: String,
        referer: String,
        accept: String
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_UA)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw NativeScraperException("GET ${response.code}: $url")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun isSubtitleTrack(track: TrustWatchTrack): Boolean {
        return track.kind.equals("captions", true) ||
            track.kind.equals("subtitles", true) ||
            track.file.endsWith(".vtt", true)
    }

    private fun languageCode(label: String): String {
        val s = label.lowercase()
        return when {
            "english" in s -> "en"
            "spanish" in s -> "es"
            "portuguese" in s -> "pt"
            "french" in s -> "fr"
            "german" in s -> "de"
            "italian" in s -> "it"
            "russian" in s -> "ru"
            "arabic" in s -> "ar"
            "malay" in s -> "ms"
            "indonesian" in s -> "id"
            "thai" in s -> "th"
            "vietnamese" in s -> "vi"
            "chinese" in s -> "zh"
            "polish" in s -> "pl"
            "japanese" in s -> "ja"
            "korean" in s -> "ko"
            else -> "und"
        }
    }

    private fun normalizeTitle(value: String): String {
        return value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun scoreTitle(target: String, candidate: String): Int {
        if (target == candidate) return 100
        if (target.isBlank() || candidate.isBlank()) return 0
        if (candidate.contains(target) || target.contains(candidate)) return 85

        val a = target.split(" ").filter { it.length >= 3 }.toSet()
        val b = candidate.split(" ").filter { it.length >= 3 }.toSet()
        if (a.isEmpty() || b.isEmpty()) return 0

        val overlap = a.intersect(b).size
        return ((overlap.toDouble() / a.size) * 100.0).toInt().coerceAtMost(80)
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun fail(message: String): Nothing {
        throw NativeScraperException(message)
    }

    private class NativeScraperException(
        message: String
    ) : IOException(message)
}
