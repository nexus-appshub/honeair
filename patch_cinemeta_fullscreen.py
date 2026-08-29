import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

# Add isFullScreen parameter
content = re.sub(
    r'(fun CinemetaWebViewPlayer\(\n.*?)(\n\s*isInPipMode: Boolean = false,)',
    r'\1\n    isFullScreen: Boolean = false,\2',
    content,
    flags=re.DOTALL
)

# Pass isFullScreen to ExoPlayerView
content = re.sub(
    r'(com\.example\.ui\.components\.ExoPlayerView\([^)]*?isFullScreen = )isInPipMode(,[^)]*?\))',
    r'\1isFullScreen\2',
    content,
    flags=re.DOTALL
)

# And fix onFullScreenToggle = { onFullScreenChange(!isInPipMode) }
content = re.sub(
    r'(onFullScreenToggle = \{ onFullScreenChange\(!)isInPipMode(\) \},)',
    r'\1isFullScreen\2',
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
print("Patched CinemetaWebViewPlayer")
