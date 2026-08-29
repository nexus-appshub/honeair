with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

old_filter = """    val filteredRelated = remember(allMediaItems, localSearchQuery, imdbId) {
        allMediaItems.filter { item ->
            val matchesSearch = localSearchQuery.isBlank() ||
                    item.title.contains(localSearchQuery, ignoreCase = true) ||
                    item.category.contains(localSearchQuery, ignoreCase = true)
            matchesSearch && item.imdbId != imdbId
        }
    }"""

new_filter = """    val filteredRelated = remember(allMediaItems, localSearchQuery, imdbId, type) {
        allMediaItems.filter { item ->
            val matchesSearch = localSearchQuery.isBlank() ||
                    item.title.contains(localSearchQuery, ignoreCase = true) ||
                    item.category.contains(localSearchQuery, ignoreCase = true)
            
            val isMoviePlaying = !isSeries && !type.equals("anime", ignoreCase = true)
            val isAnimePlaying = type.equals("anime", ignoreCase = true)
            
            val typeMatch = if (isAnimePlaying) {
                item.category.contains("anime", ignoreCase = true)
            } else if (isMoviePlaying) {
                item.type.equals("movie", ignoreCase = true) || item.category.contains("movie", ignoreCase = true)
            } else {
                true // for series
            }
            
            matchesSearch && item.imdbId != imdbId && typeMatch
        }
    }"""

content = content.replace(old_filter, new_filter)

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.write(content)
