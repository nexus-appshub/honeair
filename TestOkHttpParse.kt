import java.io.BufferedReader
import java.io.StringReader
import okhttp3.OkHttpClient
import okhttp3.Request

data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val tvgId: String
)

fun main() {
    val client = OkHttpClient.Builder().build()
    val request = Request.Builder().url("https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/refs/heads/main/movies.m3u8").build()
    val rawM3u = client.newCall(request).execute().body?.string() ?: ""
    
    val channels = mutableListOf<IptvChannel>()
    val reader = BufferedReader(StringReader(rawM3u))
    var currentName = ""
    var currentLogo = ""
    var currentGroup = ""
    var currentTvgId = ""
    reader.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("#EXTINF:")) {
            val commaIndex = trimmed.lastIndexOf(',')
            if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                currentName = trimmed.substring(commaIndex + 1).trim()
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
        }
    }
    println("Parsed ${channels.size} channels")
}
