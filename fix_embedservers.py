with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

target = """    val embedServers = remember(imdbId, isSeries, currentSeason, currentEpisode, encodedTitle, allMediaItems) {
        val isImdb = imdbId.startsWith("tt")"""

replacement = """    val embedServers = remember(imdbId, isSeries, currentSeason, currentEpisode, encodedTitle, allMediaItems, nativeStreamUrl) {
        val servers = mutableListOf<Pair<String, String>>()
        if (nativeStreamUrl != null) {
            servers.add(Pair("Native Direct Server (M3U8/MP4) ⚡", nativeStreamUrl))
        }
        val isImdb = imdbId.startsWith("tt")"""

content = content.replace(target, replacement)

target_2 = """        val isBangla = currentMediaItem?.category?.equals("Bangla Cinema & Natok", ignoreCase = true) == true
        
        val servers = mutableListOf<Pair<String, String>>()
        
        val vidsrcSbsUrl = if (!isSeries) "https://vidsrc.sbs/embed/movie/$imdbId" else "https://vidsrc.sbs/embed/tv/$imdbId/$currentSeason/$currentEpisode\""""

replacement_2 = """        val isBangla = currentMediaItem?.category?.equals("Bangla Cinema & Natok", ignoreCase = true) == true
        
        val vidsrcSbsUrl = if (!isSeries) "https://vidsrc.sbs/embed/movie/$imdbId" else "https://vidsrc.sbs/embed/tv/$imdbId/$currentSeason/$currentEpisode\""""

content = content.replace(target_2, replacement_2)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
