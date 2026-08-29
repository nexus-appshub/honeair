import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

fun main() {
    try {
        val url = URL("https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/refs/heads/main/movies.m3u8")
        val conn = url.openConnection()
        val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
        var lines = 0
        while (reader.readLine() != null) lines++
        println("Success: $lines lines")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
