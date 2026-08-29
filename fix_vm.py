import re

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'r') as f:
    content = f.read()

# First remove ALL of them
content = content.replace("            _activeMediaSeason.value = season\n            _activeMediaEpisode.value = episode\n", "")

# Then carefully insert it ONLY in playMediaItem
# fun playMediaItem(item: MediaItem?, season: Int = 1, episode: Int = 1) {
#     if (item == null) {
#         _activeChannel.value = null
#         _activeMediaItem.value = null
#         _isFullScreen.value = false
#         return
#     }
# 
#     viewModelScope.launch {
#         val effectiveItem = item.copy(imdbId = item.imdbId ?: item.id)

pattern = r'(fun playMediaItem\(item: MediaItem\?, season: Int = 1, episode: Int = 1\) \{[\s\S]*?viewModelScope\.launch \{)'
replacement = r'\1\n            _activeMediaSeason.value = season\n            _activeMediaEpisode.value = episode'

content = re.sub(pattern, replacement, content, count=1)

with open('app/src/main/java/com/example/ui/viewmodel/StreamViewModel.kt', 'w') as f:
    f.write(content)
