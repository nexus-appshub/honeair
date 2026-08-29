fun normalizeMediaTitle(raw: String): String {
    return raw.lowercase()
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\[.*?\\]"), "")
        .replace(Regex("[^a-z0-9\\u0980-\\u09ff]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun isChannelMatchForMedia(channelName: String, itemTitle: String, itemImdb: String, channelTvgId: String, channelUrl: String): Boolean {
    val cleanItemTitle = normalizeMediaTitle(itemTitle).replace(" ", "")
    val cleanChannelName = normalizeMediaTitle(channelName).replace(" ", "")
    val cleanItemImdb = itemImdb.lowercase().trim()

    if (cleanItemImdb.isNotEmpty() && cleanItemImdb.startsWith("tt")) {
        val chTvgId = channelTvgId.lowercase().trim()
        if (chTvgId.contains(cleanItemImdb) || channelUrl.lowercase().contains(cleanItemImdb)) {
            return true
        }
    }

    if (cleanItemTitle.isNotEmpty() && cleanChannelName.isNotEmpty()) {
        if (cleanChannelName == cleanItemTitle) return true
        
        if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
            return true
        }
        
        val cleanItemTitleWithSpaces = normalizeMediaTitle(itemTitle)
        val cleanChannelNameWithSpaces = normalizeMediaTitle(channelName)
        
        val itemWords = cleanItemTitleWithSpaces.split(" ").filter { it.isNotBlank() }
        val channelWords = cleanChannelNameWithSpaces.split(" ").filter { it.isNotBlank() }
        
        if (itemWords.isNotEmpty() && channelWords.isNotEmpty()) {
            if (itemWords.all { word -> channelWords.any { it.contains(word) || word.contains(it) } }) {
                return true
            }
        }
    }
    return false
}

fun main() {
    val itemTitle = "avatar aang: the last airbender"
    val channelName = "The Legend Of Aang The Last Airbender (2026)"
    println(isChannelMatchForMedia(channelName, itemTitle, "", "", ""))
}
