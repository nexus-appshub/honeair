fun normalizeMediaTitle(raw: String): String {
    return raw.lowercase()
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\[.*?\\]"), "")
        .replace(Regex("[^a-z0-9\\u0980-\\u09ff]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun main() {
    val itemTitle = "72 Hours"
    val channelName = "72 Hours (2026) 🆕"
    
    val cleanItemTitle = normalizeMediaTitle(itemTitle)
    val cleanChannelName = normalizeMediaTitle(channelName)
    
    println("cleanItemTitle: '$cleanItemTitle'")
    println("cleanChannelName: '$cleanChannelName'")
    
    var matched = false
    if (cleanItemTitle.isNotEmpty() && cleanChannelName.isNotEmpty()) {
        if (cleanChannelName == cleanItemTitle) matched = true
        
        if (!matched && cleanItemTitle.length >= 3 && cleanChannelName.length >= 3) {
            if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                matched = true
            }
        }
    }
    println("Matched: $matched")
}
