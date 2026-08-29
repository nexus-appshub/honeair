import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

# 1. Add playerBgColor definition and use it for outer Box background
old_box = 'Box(modifier = modifier.fillMaxSize().background(Color.Black)) {'
new_box = '''    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val playerBgColor = if (isDark) SpaceBlack else Color(0xFFF5F5F7)

    Box(modifier = modifier.fillMaxSize().background(playerBgColor)) {'''

if old_box in content:
    content = content.replace(old_box, new_box)
    print("Replaced outer Box background with playerBgColor")
else:
    print("old_box not found")

# 2. Make header buttons slightly smaller
# Close player button (36.dp -> 32.dp, 20.dp -> 16.dp)
content = content.replace(
    """.size(36.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.ArrowBack,\n                                contentDescription = \"Close Player\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(20.dp)""",
    """.size(32.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.ArrowBack,\n                                contentDescription = \"Close Player\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(16.dp)"""
)

# PiP button (34.dp -> 30.dp, 18.dp -> 15.dp)
content = content.replace(
    """.size(34.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.PictureInPictureAlt,\n                                contentDescription = \"PiP\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(18.dp)""",
    """.size(30.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.PictureInPictureAlt,\n                                contentDescription = \"PiP\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(15.dp)"""
)

# Server badge height (32.dp -> 28.dp)
content = content.replace(
    """.height(32.dp)\n                                .background(DeepSlate, RoundedCornerShape(12.dp))""",
    """.height(28.dp)\n                                .background(DeepSlate, RoundedCornerShape(10.dp))"""
)

# Download button (34.dp -> 30.dp, 18.dp -> 15.dp)
content = content.replace(
    """.size(34.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.Download,\n                                contentDescription = \"Download Video\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(18.dp)""",
    """.size(30.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.Download,\n                                contentDescription = \"Download Video\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(15.dp)"""
)

# Search button (34.dp -> 30.dp, 18.dp -> 15.dp)
content = content.replace(
    """.size(34.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.Search,\n                                contentDescription = \"Search Related\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(18.dp)""",
    """.size(30.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)\n                        ) {\n                            Icon(\n                                imageVector = Icons.Default.Search,\n                                contentDescription = \"Search Related\",\n                                tint = NeonCyan,\n                                modifier = Modifier.size(15.dp)"""
)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)

print("Updated CinemetaWebViewPlayer successfully.")
