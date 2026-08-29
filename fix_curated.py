import re

with open("app/src/main/java/com/example/data/repository/MediaRepository.kt", "r") as f:
    content = f.read()

replacements = {
    "ani_1": ("tt9323232", "series"), # Solo Leveling
    "ani_2": ("tt12343534", "series"), # Jujutsu Kaisen
    "ani_3": ("tt9335498", "series"), # Demon Slayer
    "ani_4": ("tt2560140", "series"), # Attack on Titan
    "ani_5": ("tt0388629", "series"), # One Piece
    "ani_short_1": ("tt12590266", "series"), # Cyberpunk
    "ani_short_2": ("tt13616990", "series"), # Chainsaw Man
    "kdrama_cur_1": ("tt10850932", "series"), # Crash Landing
    "kdrama_cur_2": ("tt13433812", "series"), # Vincenzo
    "hindi_cur_1": ("tt9110468", "series"), # Mirzapur
    "hindi_cur_2": ("tt13721956", "series"), # Money Heist Korea
    "mov_1": ("tt1254207", "movie"), # Big Buck Bunny -> Let's change this to a real movie like Inception: tt1375666
    "mov_2": ("tt1727776", "movie"), # Sintel -> Interstellar tt0816692
    "mov_3": ("tt2285752", "movie"), # Tears of Steel -> The Matrix tt0133093
    "mov_4": ("tt15239678", "movie"), # Dune Part Two
    "series_1": ("tt0903747", "series"), # Breaking Bad
    "short_tv_1": ("tt0944947", "series") # Game of Thrones
}

for item_id, (imdb, type) in replacements.items():
    # Find the MediaItem block for this id
    pattern = r'(id = "' + item_id + r'".*?streamUrl = )".*?"(.*?isStreamable = true)'
    stream_url = f'"https://vidsrc.to/embed/{ "movie" if type == "movie" else "tv" }/{imdb}{"" if type == "movie" else "/1/1"}"'
    
    # We need to add imdbId and type to the MediaItem as well
    replacement = r'\1' + stream_url + r'\2,\n                imdbId = "' + imdb + r'",\n                type = "' + type + r'"'
    
    content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/data/repository/MediaRepository.kt", "w") as f:
    f.write(content)
