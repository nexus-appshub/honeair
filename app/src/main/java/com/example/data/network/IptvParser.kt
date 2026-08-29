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

object IptvParser {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("X-App-Version", com.example.BuildConfig.VERSION_CODE.toString())
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun fetchRawContent(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download IPTV list: ${response.code} ${response.message}")
            response.body?.string() ?: ""
        }
    }

    fun parseIndex(rawM3u: String): List<IptvPlaylist> {
        val playlists = mutableListOf<IptvPlaylist>()
        val reader = BufferedReader(StringReader(rawM3u))
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "All"

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                currentName = parseAttribute(trimmed, "tvg-name")
                if (currentName.isEmpty()) {
                    currentName = parseAttribute(trimmed, "tvg-id")
                }
                currentLogo = parseAttribute(trimmed, "tvg-logo")
                currentGroup = parseAttribute(trimmed, "group-title")

                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    val displayName = trimmed.substring(commaIndex + 1).trim()
                    if (currentName.isEmpty()) {
                        currentName = displayName
                    }
                }
            } else if (trimmed.startsWith("http")) {
                val name = currentName.ifEmpty { trimmed.substringAfterLast("/") }
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

    fun parseChannels(rawM3u: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val reader = BufferedReader(StringReader(rawM3u))
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentTvgId = ""

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                currentName = parseAttribute(trimmed, "tvg-name")
                currentLogo = parseAttribute(trimmed, "tvg-logo")
                currentGroup = parseAttribute(trimmed, "group-title")
                currentTvgId = parseAttribute(trimmed, "tvg-id")

                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    val displayName = trimmed.substring(commaIndex + 1).trim()
                    if (currentName.isEmpty()) {
                        currentName = displayName
                    }
                }
            } else if (trimmed.startsWith("http")) {
                val name = currentName.ifEmpty { trimmed.substringAfterLast("/") }
                channels.add(
                    IptvChannel(
                        name = name,
                        url = trimmed,
                        logo = currentLogo,
                        group = currentGroup.ifEmpty { "Channels" },
                        tvgId = currentTvgId
                    )
                )
                currentName = ""
                currentLogo = ""
                currentGroup = ""
                currentTvgId = ""
            }
        }
        return channels
    }

    private fun parseAttribute(line: String, key: String): String {
        val search = "$key=\""
        val startIndex = line.indexOf(search)
        if (startIndex == -1) return ""
        val valueStart = startIndex + search.length
        val endIndex = line.indexOf('"', valueStart)
        if (endIndex == -1) return ""
        return line.substring(valueStart, endIndex)
    }
}
