import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

# Make header buttons very compact and small
content = content.replace(
    'modifier = Modifier.size(34.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)',
    'modifier = Modifier.size(28.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)'
)
content = content.replace(
    'modifier = Modifier.size(36.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)',
    'modifier = Modifier.size(28.dp)\n                                .clip(CircleShape)\n                                .background(DeepSlate)'
)
content = content.replace('modifier = Modifier.size(18.dp)', 'modifier = Modifier.size(14.dp)')
content = content.replace('modifier = Modifier.size(20.dp)', 'modifier = Modifier.size(14.dp)')

content = content.replace('.height(32.dp)', '.height(26.dp)')
content = content.replace('horizontalArrangement = Arrangement.spacedBy(8.dp)', 'horizontalArrangement = Arrangement.spacedBy(4.dp)')

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)

print("Patched CinemetaWebViewPlayer header buttons to be compact and small.")
