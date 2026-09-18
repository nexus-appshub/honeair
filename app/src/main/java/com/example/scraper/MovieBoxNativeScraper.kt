package com.example.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object MovieBoxNativeScraper {
    private const val TAG = "MovieBoxNativeScraper"
    private const val APUSEEN_BASE = "https://api.apuseencom.com/webed-hdapp"
    private const val HAKUNA_BASE = "https://hakunamatatavideo.com"
    private const val AONEROOM_BASE = "https://tv.aoneroom.com"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    private const val MBOX_APP_UA = "com.community.mbox.tv/50040011 (Linux; U; Android 9; en_US; 23078RKD5C; Build/PQ3B.190801.07131748; Cronet/151.0.7922.47)"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false) // Handle 307 manual redirection to capture stream URL
            .followSslRedirects(false)
            .build()
    }

    private var cachedToken: String? = null

    /**
     * Search MovieBox Subject ID by title across Apuseencom and Aoneroom.
     */
    suspend fun searchSubjectId(title: String): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val cleanTitle = title.replace(Regex("\\s*\\(\\d{4}\\)\\s*"), "").trim()

        // 1. Try Apuseencom Search API: GET https://api.apuseencom.com/webed-hdapp/v1/everyone-search?keyword={query}
        try {
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchUrl = "$APUSEEN_BASE/v1/everyone-search?keyword=$encodedTitle"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://themoviebox.xyz/")
                .header("Origin", "https://themoviebox.xyz")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val data = json.optJSONObject("data")
                val list = data?.optJSONArray("list") ?: json.optJSONArray("data")
                if (list != null && list.length() > 0) {
                    for (i in 0 until list.length()) {
                        val item = list.getJSONObject(i)
                        val id = item.optString("subject_id").ifBlank { item.optString("id") }
                        if (id.isNotBlank()) {
                            Log.d(TAG, "Found Apuseencom subjectId for $title: $id")
                            return@withContext id
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Apuseencom search failed for $title: ${e.message}")
        }

        // 2. Try Aoneroom search fallback
        try {
            val token = getVisitorToken()
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val aoneUrl = "$AONEROOM_BASE/wefeed-tv-bff/search/result?keyword=$encodedTitle&page=1&perPage=10"
            val request = Request.Builder()
                .url(aoneUrl)
                .header("User-Agent", MBOX_APP_UA)
                .header("Authorization", "Bearer $token")
                .header("X-Client-Status", "1")
                .header("X-Play-Mode", "stream")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val items = json.optJSONObject("data")?.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val id = items.getJSONObject(0).optString("subjectId")
                    if (id.isNotBlank()) {
                        Log.d(TAG, "Found Aoneroom subjectId for $title: $id")
                        return@withContext id
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aoneroom search failed for $title: ${e.message}")
        }

        null
    }

    /**
     * Extract stream info via Hakunamatata Subdub resolver (307 redirect) and Aoneroom play-info.
     */
    suspend fun getStreamInfo(subjectId: String, season: Int = 0, episode: Int = 0): ScrapedStreamResult? = withContext(Dispatchers.IO) {
        if (subjectId.isBlank()) return@withContext null

        // 1. Try Hakunamatata Multi-Source Resolver: GET https://hakunamatatavideo.com/subdub/v2?id={subject_id}&source=vidbox&format=hls
        try {
            val sources = listOf("vidbox", "film", "netflix", "plex", "emby", "stream", "hls")
            for (source in sources) {
                val hakunaUrl = "$HAKUNA_BASE/subdub/v2?id=$subjectId&source=$source&format=hls"
                val request = Request.Builder()
                    .url(hakunaUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "https://themoviebox.xyz/")
                    .header("Origin", "https://themoviebox.xyz")
                    .build()

                val response = httpClient.newCall(request).execute()
                val code = response.code

                // Check 307/302/301 redirection location header for master .m3u8
                if (code in 300..399) {
                    val redirectUrl = response.header("Location")
                    if (!redirectUrl.isNullOrBlank()) {
                        Log.d(TAG, "Successfully extracted MovieBox Hakunamatata stream (Redirect): $redirectUrl")
                        return@withContext ScrapedStreamResult(
                            streamUrl = redirectUrl,
                            headers = mapOf(
                                "User-Agent" to DEFAULT_UA,
                                "Referer" to "https://themoviebox.xyz/",
                                "Origin" to "https://themoviebox.xyz"
                            ),
                            referer = "https://themoviebox.xyz/"
                        )
                    }
                }

                // If 200 OK, check JSON payload or direct stream URL
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.contains(".m3u8") || body.contains(".mp4") || body.contains("url")) {
                        try {
                            val json = JSONObject(body)
                            val streamUrl = json.optString("url", "").ifBlank { json.optString("stream_url", "") }
                            if (streamUrl.isNotBlank()) {
                                return@withContext ScrapedStreamResult(
                                    streamUrl = streamUrl,
                                    headers = mapOf(
                                        "User-Agent" to DEFAULT_UA,
                                        "Referer" to "https://themoviebox.xyz/"
                                    ),
                                    referer = "https://themoviebox.xyz/"
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hakunamatata resolver error: ${e.message}")
        }

        // 2. Try Aoneroom Direct CDN fallback
        try {
            val token = getVisitorToken()
            val aonePlayUrl = "$AONEROOM_BASE/wefeed-tv-bff/subject/play-info?subjectId=$subjectId&se=$season&ep=$episode"
            val request = Request.Builder()
                .url(aonePlayUrl)
                .header("User-Agent", MBOX_APP_UA)
                .header("Authorization", "Bearer $token")
                .header("X-Client-Status", "1")
                .header("X-Play-Mode", "stream")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val data = json.optJSONObject("data")
                if (data != null) {
                    val resources = data.optJSONArray("resources")
                    val mp4Url = if (resources != null && resources.length() > 0) resources.getJSONObject(0).optString("url") else null

                    val streams = data.optJSONArray("streams")
                    val dashUrl = if (streams != null && streams.length() > 0) streams.getJSONObject(0).optString("url") else null
                    val signCookie = if (streams != null && streams.length() > 0) streams.getJSONObject(0).optString("signCookie") else ""

                    val finalUrl = mp4Url ?: dashUrl
                    if (!finalUrl.isNullOrEmpty()) {
                        val headerMap = mutableMapOf(
                            "User-Agent" to DEFAULT_UA
                        )
                        if (signCookie.isNotEmpty()) {
                            headerMap["Cookie"] = signCookie
                        }
                        return@withContext ScrapedStreamResult(
                            streamUrl = finalUrl,
                            cookie = signCookie,
                            headers = headerMap,
                            referer = "https://themoviebox.xyz/"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aoneroom play-info failed: ${e.message}")
        }

        null
    }

    private suspend fun getVisitorToken(): String = withContext(Dispatchers.IO) {
        cachedToken?.let { return@withContext it }
        try {
            val url = URL("$AONEROOM_BASE/wefeed-tv-bff/user/visitor-login")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", MBOX_APP_UA)
                setRequestProperty("X-Client-Status", "1")
                setRequestProperty("X-Play-Mode", "stream")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val token = json.optJSONObject("data")?.optString("token")
                if (!token.isNullOrEmpty()) {
                    cachedToken = token
                    return@withContext token
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Visitor login failed: ${e.message}")
        }
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjYwNDk1NjQ5MTA2NjkyMzIsImV4cCI6MTc5NTM1ODUwMn0.ZKkU5-K-Hw63EHFcgUQ"
    }
}
