import re

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

target = """            Pair("VidSrc ME", if (!isSeries) "https://vidsrc.me/embed/movie?$tmdbOrImdb=$imdbId" else "https://vidsrc.me/embed/tv?$tmdbOrImdb=$imdbId&season=$currentSeason&episode=$currentEpisode"),"""

replacement = """            Pair("VidSrc ME", if (!isSeries) "https://vidsrc.net/embed/movie?imdb=$imdbId" else "https://vidsrc.net/embed/tv?imdb=$imdbId&season=$currentSeason&episode=$currentEpisode"),"""

new_content = content.replace(target, replacement)
if new_content == content:
    print("NO MATCH FOUND")
else:
    with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
        f.write(new_content)
    print("REPLACED SUCCESSFULLY")
