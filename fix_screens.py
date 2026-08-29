import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

def replacer(match):
    # match.group(0) is the full string
    # we want to extract the variable name (item, mediaItem, selectedItem)
    var_match = re.search(r'WebPlayerState\(item\s*=\s*([a-zA-Z0-9_]+)', match.group(0))
    if var_match:
        var_name = var_match.group(1)
        return f"viewModel.playMediaItem({var_name}, 1, 1)\n                                                        onNavigateToPlayer()"
    return match.group(0)

# Replace all occurrences where it's wrapped in if/else
pattern = re.compile(r'if\s*\([a-zA-Z0-9_]+\.imdbId\s*!=\s*null\)\s*\{\s*activeWebPlayer\s*=\s*WebPlayerState[^}]+\}\s*else\s*\{\s*viewModel\.playMediaItem[^}]+\}')
new_content = pattern.sub(replacer, content)

# Also there's one at 8577:
# if (item.imdbId != null) {
#     activeWebPlayer = WebPlayerState(item = item, season = season, episode = episode)
# } else {
#     viewModel.playMediaItem(item)
#     onNavigateToPlayer()
# }
def replacer_with_season(match):
    var_match = re.search(r'WebPlayerState\(item\s*=\s*([a-zA-Z0-9_]+),\s*season\s*=\s*([a-zA-Z0-9_]+),\s*episode\s*=\s*([a-zA-Z0-9_]+)\)', match.group(0))
    if var_match:
        var_name = var_match.group(1)
        season_var = var_match.group(2)
        ep_var = var_match.group(3)
        return f"viewModel.playMediaItem({var_name}, {season_var}, {ep_var})\n                    onNavigateToPlayer()"
    return match.group(0)

pattern2 = re.compile(r'if\s*\([a-zA-Z0-9_]+\.imdbId\s*!=\s*null\)\s*\{\s*activeWebPlayer\s*=\s*WebPlayerState\([^)]+season\s*=\s*season[^}]+\}\s*else\s*\{\s*viewModel\.playMediaItem[^}]+\}')
new_content = pattern2.sub(replacer_with_season, new_content)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(new_content)

print("Done")
