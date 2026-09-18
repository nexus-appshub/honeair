import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "fun ArchivedChannelsSubPage(\n    viewModel: StreamViewModel,\n    onBack: () -> Unit\n) {\n    val prefs by viewModel.channelPreferences.collectAsState()",
    "fun ArchivedChannelsSubPage(\n    viewModel: StreamViewModel,\n    onBack: () -> Unit\n) {\n    val isDark = androidx.compose.foundation.isSystemInDarkTheme()\n    val textColor = if (isDark) Color.White else Color.Black\n    val prefs by viewModel.channelPreferences.collectAsState()"
)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
