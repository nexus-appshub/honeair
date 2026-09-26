package com.example.data.network

import android.util.Log
import com.example.data.model.IptvChannel
import com.example.data.model.IptvPlaylist
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object IptvParser {
    private val client = OkHttpClient.Builder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        })
        .connectionPool(okhttp3.ConnectionPool(30, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("X-App-Version", com.example.BuildConfig.VERSION_CODE.toString())
                .build()
            chain.proceed(request)
        }
        .build()

    private val attributePattern = Pattern.compile("([a-zA-Z0-9_\\-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))")

    suspend fun fetchRawContent(url: String): String = withContext(Dispatchers.IO) {
        var targetUrl = url.trim()
        val requestBuilder = Request.Builder()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "*/*")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")

        // For HTTP/HTTPS links (including GitHub raw links), append timestamp query to bypass CDN caching
        if (targetUrl.startsWith("http://", ignoreCase = true) || targetUrl.startsWith("https://", ignoreCase = true)) {
            val cacheBuster = "_ts=${System.currentTimeMillis()}"
            val separator = if (targetUrl.contains("?")) "&" else "?"
            val uncachedUrl = "$targetUrl$separator$cacheBuster"
            requestBuilder.url(uncachedUrl)
        } else {
            requestBuilder.url(targetUrl)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                // If query param caused issue, retry with exact original URL
                if (response.code == 404 || response.code == 400) {
                    val fallbackReq = Request.Builder()
                        .url(targetUrl)
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
                        .build()
                    client.newCall(fallbackReq).execute().use { fbResponse ->
                        if (!fbResponse.isSuccessful) throw Exception("Failed to download IPTV list: ${fbResponse.code} ${fbResponse.message}")
                        val content = fbResponse.body?.string() ?: ""
                        return@withContext cleanRawM3u(content)
                    }
                }
                throw Exception("Failed to download IPTV list: ${response.code} ${response.message}")
            }
            val content = response.body?.string() ?: ""
            cleanRawM3u(content)
        }
    }

    private fun cleanRawM3u(raw: String): String {
        var cleaned = raw
        if (cleaned.startsWith("\uFEFF")) {
            cleaned = cleaned.substring(1)
        }
        return cleaned
    }

    fun parseIndex(rawM3u: String): List<IptvPlaylist> {
        val playlists = mutableListOf<IptvPlaylist>()
        val reader = BufferedReader(StringReader(cleanRawM3u(rawM3u)))
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "All"

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine

            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                val parsedMeta = parseExtinfLine(trimmed)
                currentName = parsedMeta.displayName.ifBlank { parsedMeta.tvgName }.ifBlank { parsedMeta.tvgId }
                currentLogo = parsedMeta.tvgLogo
                currentGroup = parsedMeta.groupTitle.ifBlank { "All" }
            } else if (trimmed.startsWith("#EXTGRP:", ignoreCase = true)) {
                currentGroup = trimmed.substringAfter(":").trim().ifBlank { currentGroup }
            } else if (!trimmed.startsWith("#")) {
                val name = currentName.ifEmpty { trimmed.substringAfterLast("/").substringBefore("?") }.ifEmpty { "Playlist ${playlists.size + 1}" }
                playlists.add(
                    IptvPlaylist(
                        name = name,
                        url = trimmed,
                        group = currentGroup.ifEmpty { "All" },
                        logo = currentLogo
                    )
                )
                currentName = ""
                currentLogo = ""
                currentGroup = "All"
            }
        }
        return playlists
    }

    data class ExtinfMetadata(
        val displayName: String = "",
        val tvgName: String = "",
        val tvgLogo: String = "",
        val groupTitle: String = "",
        val tvgId: String = ""
    )

    fun parseChannels(rawM3u: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val reader = BufferedReader(StringReader(cleanRawM3u(rawM3u)))
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentTvgId = ""
        val currentHeaders = mutableMapOf<String, String>()

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine

            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                val meta = parseExtinfLine(trimmed)
                // Use display name if available, otherwise tvg-name, otherwise tvg-id
                currentName = meta.displayName.ifBlank { meta.tvgName }.ifBlank { meta.tvgId }
                currentLogo = meta.tvgLogo
                currentGroup = meta.groupTitle
                currentTvgId = meta.tvgId
            } else if (trimmed.startsWith("#EXTGRP:", ignoreCase = true)) {
                currentGroup = trimmed.substringAfter(":").trim()
            } else if (trimmed.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true)) {
                currentHeaders["User-Agent"] = trimmed.substringAfter("=", "").trim()
            } else if (trimmed.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) || trimmed.startsWith("#EXTVLCOPT:http-referer=", ignoreCase = true)) {
                currentHeaders["Referer"] = trimmed.substringAfter("=", "").trim()
            } else if (trimmed.startsWith("#EXTVLCOPT:http-origin=", ignoreCase = true)) {
                currentHeaders["Origin"] = trimmed.substringAfter("=", "").trim()
            } else if (trimmed.startsWith("#EXTHTTP:", ignoreCase = true)) {
                try {
                    val jsonStr = trimmed.substringAfter(":", "").trim()
                    val json = org.json.JSONObject(jsonStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = json.optString(key, "")
                        if (value.isNotBlank()) {
                            currentHeaders[key] = value
                        }
                    }
                } catch (_: Exception) {}
            } else if (trimmed.startsWith("#KODIPROP:inputstream.adaptive.manifest_headers=", ignoreCase = true) ||
                trimmed.startsWith("#KODIPROP:inputstream.adaptive.stream_headers=", ignoreCase = true)) {
                val headerContent = trimmed.substringAfter("=", "").trim()
                headerContent.split("&").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank()) {
                        currentHeaders[parts[0].trim()] = parts[1].trim()
                    }
                }
            } else if (!trimmed.startsWith("#")) {
                var streamUrl = trimmed
                val channelHeaders = mutableMapOf<String, String>()
                channelHeaders.putAll(currentHeaders)

                // Check pipe syntax: http://stream.m3u8|User-Agent=...&Referer=...
                if (streamUrl.contains("|")) {
                    val parts = streamUrl.split("|", limit = 2)
                    streamUrl = parts[0].trim()
                    if (parts.size > 1) {
                        parts[1].split("&").forEach { pair ->
                            val kv = pair.split("=", limit = 2)
                            if (kv.size == 2 && kv[0].isNotBlank()) {
                                channelHeaders[kv[0].trim()] = kv[1].trim()
                            }
                        }
                    }
                }

                val finalName = currentName.ifEmpty {
                    streamUrl.substringAfterLast("/").substringBefore("?").ifEmpty { "Channel ${channels.size + 1}" }
                }

                channels.add(
                    IptvChannel(
                        name = finalName,
                        url = streamUrl,
                        logo = currentLogo,
                        group = currentGroup.ifEmpty { "Live TV" },
                        tvgId = currentTvgId,
                        headers = channelHeaders
                    )
                )

                // Reset per-channel metadata
                currentName = ""
                currentLogo = ""
                currentGroup = ""
                currentTvgId = ""
                currentHeaders.clear()
            }
        }
        return channels
    }

    private fun parseExtinfLine(line: String): ExtinfMetadata {
        // Separate attributes from display title by finding comma outside of quotes
        var inDoubleQuotes = false
        var inSingleQuotes = false
        var commaIndex = -1

        for (i in line.indices) {
            val char = line[i]
            if (char == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes
            } else if (char == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes
            } else if (char == ',' && !inDoubleQuotes && !inSingleQuotes) {
                commaIndex = i
                break
            }
        }

        val attributesPart = if (commaIndex != -1) line.substring(0, commaIndex) else line
        val displayName = if (commaIndex != -1 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else {
            ""
        }

        var tvgName = ""
        var tvgLogo = ""
        var groupTitle = ""
        var tvgId = ""

        val matcher = attributePattern.matcher(attributesPart)
        while (matcher.find()) {
            val key = matcher.group(1)?.lowercase() ?: continue
            val value = (matcher.group(2) ?: matcher.group(3) ?: matcher.group(4) ?: "").trim()
            if (value.isBlank()) continue

            when (key) {
                "tvg-name" -> tvgName = value
                "tvg-logo" -> tvgLogo = value
                "group-title", "group" -> groupTitle = value
                "tvg-id" -> tvgId = value
            }
        }

        return ExtinfMetadata(
            displayName = displayName,
            tvgName = tvgName,
            tvgLogo = tvgLogo,
            groupTitle = groupTitle,
            tvgId = tvgId
        )
    }
}
