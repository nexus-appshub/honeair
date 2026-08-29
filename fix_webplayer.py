import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# Instead of modifying the hundreds of places, we can just write a regex that replaces the block:
# if (item.imdbId != null) {
#     activeWebPlayer = WebPlayerState(item = item, season = 1, episode = 1)
# } else {
#     viewModel.playMediaItem(item)
#     onNavigateToPlayer()
# }
# With:
# viewModel.playMediaItem(item, season = 1, episode = 1)
# onNavigateToPlayer()

# Let's see all occurrences of activeWebPlayer assignments
matches = re.findall(r'activeWebPlayer\s*=\s*WebPlayerState\([^)]+\)', content)
print(f"Found {len(matches)} activeWebPlayer assignments")
