import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# Fix LocalVideosManagerSubPage
content = re.sub(
    r'(fun LocalVideosManagerSubPage\(\n.*?\) \{)',
    r'\1\n    val isDark = isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black',
    content
)
content = re.sub(
    r'(Text\("Local Offline Videos", color = )Color\.White(, fontSize = 20\.sp)',
    r'\1textColor\2',
    content
)
content = re.sub(
    r'(Text\("Your Offline Library", color = )Color\.White(, fontWeight = FontWeight\.Bold, fontSize = 15\.sp)',
    r'\1textColor\2',
    content
)
content = re.sub(
    r'(Icon\(Icons\.AutoMirrored\.Filled\.ArrowBack, contentDescription = "Back", tint = )Color\.White(\))',
    r'\1textColor\2',
    content
)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
