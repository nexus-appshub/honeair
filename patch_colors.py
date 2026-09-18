import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

def inject_vals(func_name, content):
    pattern = r'(fun ' + func_name + r'\([^)]*\)\s*\{)'
    replacement = r'\1\n    val isDark = isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black'
    return re.sub(pattern, replacement, content, count=1)

content = inject_vals('M3uPlaylistsManagerSubPage', content)
content = inject_vals('LocalVideosManagerSubPage', content)
content = inject_vals('ArchivedChannelsSubPage', content)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)

