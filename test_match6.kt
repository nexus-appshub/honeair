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
        
        // Contains match with safer length check
        if (cleanItemTitle.length >= 6 && cleanChannelName.length >= 6) {
            if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                return true
            }
        }
        
        // Word-level match
        val cleanItemTitleWithSpaces = normalizeMediaTitle(itemTitle)
        val cleanChannelNameWithSpaces = normalizeMediaTitle(channelName)
        val itemWords = cleanItemTitleWithSpaces.split(" ").filter { it.isNotBlank() }
        val channelWords = cleanChannelNameWithSpaces.split(" ").filter { it.isNotBlank() }
        
        val sigItemWords = itemWords.filter { it.length > 2 }
        val sigChannelWords = channelWords.filter { it.length > 2 }
        
        if (sigItemWords.isNotEmpty() && sigChannelWords.isNotEmpty()) {
            if (sigItemWords.all { w -> 
                sigChannelWords.any { cw -> 
                    w == cw || (cw.length >= 4 && w.contains(cw)) || (w.length >= 4 && cw.contains(w))
                } 
            }) {
                return true
            }
        }
    }
    return false
}

fun main() {
    val itemTitle = "avatar aang: the last airbender"
    val channelNames = listOf(
        "Avatar Fire and Ash (2025)",
        "The Last (2024)",
        "The Last Airbender",
        "Airbender"
    )
    
    for (name in channelNames) {
        if(isChannelMatchForMedia(name, itemTitle, "", "", "")) {
            println("MATCHED: " + name)
        }
    }
}
