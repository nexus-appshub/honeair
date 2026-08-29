with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "Pair(\"VidLink API\"" in line and "vidsrc.me" in line:
        lines[i] = '            Pair("VidLink API", if (!isSeries) "https://vidlink.pro/movie/$imdbId" else "https://vidlink.pro/tv/$imdbId/$currentSeason/$currentEpisode"),\n'
    elif "Pair(\"VidSrc ME\"" in line and "vidlink.pro" in line:
        lines[i] = '            Pair("VidSrc ME", if (!isSeries) "https://vidsrc.me/embed/movie?$tmdbOrImdb=$imdbId" else "https://vidsrc.me/embed/tv?$tmdbOrImdb=$imdbId&season=$currentSeason&episode=$currentEpisode"),\n'

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.writelines(lines)
