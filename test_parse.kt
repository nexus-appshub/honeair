package com.example.data.network

import java.io.BufferedReader
import java.io.StringReader

data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val tvgId: String
)

object IptvParser {
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
