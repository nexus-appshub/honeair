import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# Update SettingsScreen call to M3uPlaylistsManagerSubPage
content = re.sub(
    r'(M3uPlaylistsManagerSubPage\(\s*viewModel = viewModel,\s*onBack = \{ activeSubPage = "MAIN" \},)',
    r'\1\n                        onNavigateToAirTab = onNavigateToPlayer,',
    content
)

# Update M3uPlaylistsManagerSubPage signature
content = re.sub(
    r'(fun M3uPlaylistsManagerSubPage\(\n.*?viewModel: StreamViewModel,\n.*?onBack: \(\) -> Unit,)',
    r'\1\n    onNavigateToAirTab: () -> Unit = {},',
    content
)

# Add click action to the Card in Section 3
content = re.sub(
    r'(Card\(\n\s*colors = CardDefaults\.cardColors\(containerColor = Color\(0xFF2C2C2E\)\),\n\s*shape = RoundedCornerShape\(12\.dp\)\n\s*\) \{)',
    r'Card(\n                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),\n                        shape = RoundedCornerShape(12.dp),\n                        modifier = Modifier.clickable {\n                            viewModel.selectPlaylist(com.example.data.model.IptvPlaylist(name = pl.name, url = "custom://${pl.id}", group = "Custom"))\n                            onNavigateToAirTab()\n                        }\n                    ) {',
    content
)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
