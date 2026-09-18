import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# Fix ArchivedChannelsSubPage
content = re.sub(
    r'(fun ArchivedChannelsSubPage\(\n.*?\) \{)',
    r'\1\n    val isDark = isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black',
    content
)
content = re.sub(
    r'(Icon\(Icons\.AutoMirrored\.Filled\.ArrowBack, contentDescription = "Back", tint = )Color\.White(\))',
    r'\1textColor\2',
    content
)
content = re.sub(
    r'(Text\("Archived Channels", color = )Color\.White(, fontSize = 20\.sp)',
    r'\1textColor\2',
    content
)

# Fix M3uPlaylistsManagerSubPage
content = re.sub(
    r'(fun M3uPlaylistsManagerSubPage\(\n.*?\) \{)',
    r'\1\n    val isDark = isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black',
    content
)
content = re.sub(
    r'(Text\("M3U8 Playlists Manager", color = )Color\.White(, fontSize = 20\.sp)',
    r'\1textColor\2',
    content
)
content = re.sub(
    r'(Text\("Your Local Playlists", color = )Color\.White(, fontWeight = FontWeight\.Bold, fontSize = 16\.sp)',
    r'\1textColor\2',
    content
)

# Fix LocalVideosManagerSubPage (LocalOfflineVideosSubPage? wait, I will check the name)
with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
