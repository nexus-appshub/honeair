import re

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

old_servers = """    val embedServers = remember(imdbId, isSeries, currentSeason, currentEpisode) {
        val isImdb = imdbId.startsWith("tt")
        val tmdbOrImdb = if (isImdb) "imdb" else "tmdb"
        listOf(
            Pair("VidSrc Pro", if (!isSeries) "https://vidsrc.net/embed/movie?$tmdbOrImdb=$imdbId" else "https://vidsrc.net/embed/tv?$tmdbOrImdb=$imdbId&season=$currentSeason&episode=$currentEpisode"),
            Pair("VidLink", if (!isSeries) "https://vidlink.pro/movie/$imdbId" else "https://vidlink.pro/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("AutoEmbed", if (!isSeries) "https://player.autoembed.cc/embed/movie/$imdbId" else "https://player.autoembed.cc/embed/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("VidSrc VIP", if (!isSeries) "https://vidsrc.vip/embed/movie/$imdbId" else "https://vidsrc.vip/embed/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("SuperEmbed", if (!isSeries) "https://multiembed.mov/?video_id=$imdbId&tmdb=${if(isImdb) "0" else "1"}" else "https://multiembed.mov/?video_id=$imdbId&tmdb=${if(isImdb) "0" else "1"}&s=$currentSeason&e=$currentEpisode")
        )
    }"""

new_servers = """    val embedServers = remember(imdbId, isSeries, currentSeason, currentEpisode) {
        val isImdb = imdbId.startsWith("tt")
        val tmdbOrImdb = if (isImdb) "imdb" else "tmdb"
        listOf(
            Pair("VidLink API", if (!isSeries) "https://vidlink.pro/movie/$imdbId" else "https://vidlink.pro/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("Embed SU", if (!isSeries) "https://embed.su/embed/movie/$imdbId" else "https://embed.su/embed/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("VidSrc ME", if (!isSeries) "https://vidsrc.me/embed/movie?$tmdbOrImdb=$imdbId" else "https://vidsrc.me/embed/tv?$tmdbOrImdb=$imdbId&season=$currentSeason&episode=$currentEpisode"),
            Pair("VidSrc CC", if (!isSeries) "https://vidsrc.cc/v2/embed/movie/$imdbId" else "https://vidsrc.cc/v2/embed/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("VidSrc PRO", if (!isSeries) "https://vidsrc.pro/embed/movie/$imdbId" else "https://vidsrc.pro/embed/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("VidBinge", if (!isSeries) "https://vidbinge.dev/embed/movie/$imdbId" else "https://vidbinge.dev/embed/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("SuperEmbed", if (!isSeries) "https://multiembed.mov/?video_id=$imdbId&tmdb=${if(isImdb) "0" else "1"}" else "https://multiembed.mov/?video_id=$imdbId&tmdb=${if(isImdb) "0" else "1"}&s=$currentSeason&e=$currentEpisode"),
            Pair("SmashyStream", if (!isSeries) "https://player.smashy.stream/movie/$imdbId" else "https://player.smashy.stream/tv/$imdbId?s=$currentSeason&e=$currentEpisode"),
            Pair("Noxx", if (!isSeries) "https://vidsrc.xyz/embed/movie/$imdbId" else "https://vidsrc.xyz/embed/tv/$imdbId/$currentSeason/$currentEpisode"),
            Pair("2Embed", if (!isSeries) "https://www.2embed.cc/embed/$imdbId" else "https://www.2embed.cc/embedtv/$imdbId&s=$currentSeason&e=$currentEpisode"),
            Pair("AutoEmbed", if (!isSeries) "https://player.autoembed.cc/embed/movie/$imdbId" else "https://player.autoembed.cc/embed/tv/$imdbId/$currentSeason/$currentEpisode")
        )
    }"""

content = content.replace(old_servers, new_servers)

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.write(content)

