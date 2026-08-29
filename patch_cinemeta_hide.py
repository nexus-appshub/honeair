import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

# Hide top header
content = content.replace(
    '        // 1. Top Clean Header Bar with Control Buttons & Expandable Search Bar\n        if (!isInPipMode) {',
    '        // 1. Top Clean Header Bar with Control Buttons & Expandable Search Bar\n        if (!isInPipMode && !isFullScreen) {'
)

# Hide bottom list
content = content.replace(
    '        // 3. Scrollable Section Below Player (Search Box, Season/Episode Selector & Related Movies / Anime)\n        if (!isInPipMode) {',
    '        // 3. Scrollable Section Below Player (Search Box, Season/Episode Selector & Related Movies / Anime)\n        if (!isInPipMode && !isFullScreen) {'
)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
print("Patched CinemetaWebViewPlayer to hide header/footer in full screen")
