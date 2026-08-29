import re

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'r') as f:
    content = f.read()

target = """    private fun isChannelMatchForMedia(channel: IptvChannel, item: MediaItem): Boolean {
        val cleanItemTitle = normalizeMediaTitle(item.title)
        val cleanChannelName = normalizeMediaTitle(channel.name)
        val cleanItemImdb = item.imdbId?.lowercase()?.trim() ?: ""

        if (cleanItemImdb.isNotEmpty() && cleanItemImdb.startsWith("tt")) {
            val chTvgId = channel.tvgId.lowercase().trim()
            if (chTvgId.contains(cleanItemImdb) || channel.url.lowercase().contains(cleanItemImdb)) {
                return true
            }
        }

        if (cleanItemTitle.isNotEmpty() && cleanChannelName.isNotEmpty()) {
            if (cleanChannelName == cleanItemTitle) return true
            
            // Contains match
            if (cleanItemTitle.length >= 3 && cleanChannelName.length >= 3) {
                if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                    return true
                }
            }
            
            // Word-level match
            val itemWords = cleanItemTitle.split(" ").filter { it.isNotBlank() }
            val channelWords = cleanChannelName.split(" ").filter { it.isNotBlank() }
            
            if (itemWords.isNotEmpty() && channelWords.isNotEmpty()) {
                // If all words in the movie title exist in the channel name
                if (itemWords.all { word -> channelWords.contains(word) }) {
                    return true
                }
            }
        }
        return false
    }"""

replacement = """    private fun isChannelMatchForMedia(channel: IptvChannel, item: MediaItem): Boolean {
        val cleanItemTitle = normalizeMediaTitle(item.title).replace(" ", "")
        val cleanChannelName = normalizeMediaTitle(channel.name).replace(" ", "")
        val cleanItemImdb = item.imdbId?.lowercase()?.trim() ?: ""

        if (cleanItemImdb.isNotEmpty() && cleanItemImdb.startsWith("tt")) {
            val chTvgId = channel.tvgId.lowercase().trim()
            if (chTvgId.contains(cleanItemImdb) || channel.url.lowercase().contains(cleanItemImdb)) {
                return true
            }
        }

        if (cleanItemTitle.isNotEmpty() && cleanChannelName.isNotEmpty()) {
            if (cleanChannelName == cleanItemTitle) return true
            
            if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                return true
            }
            
            val cleanItemTitleWithSpaces = normalizeMediaTitle(item.title)
            val cleanChannelNameWithSpaces = normalizeMediaTitle(channel.name)
            
            val itemWords = cleanItemTitleWithSpaces.split(" ").filter { it.isNotBlank() }
            val channelWords = cleanChannelNameWithSpaces.split(" ").filter { it.isNotBlank() }
            
            if (itemWords.isNotEmpty() && channelWords.isNotEmpty()) {
                if (itemWords.all { word -> channelWords.any { it.contains(word) || word.contains(it) } }) {
                    return true
                }
            }
        }
        return false
    }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'w') as f:
    f.write(content)
