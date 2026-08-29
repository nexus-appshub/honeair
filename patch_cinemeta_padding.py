import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

target = '''        // 1. Top Clean Header Bar with Control Buttons & Expandable Search Bar
        if (!isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 4.dp)
            ) {'''

replacement = '''        // 1. Top Clean Header Bar with Control Buttons & Expandable Search Bar
        if (!isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 4.dp)
            ) {'''

if target in content:
    content = content.replace(target, replacement)
    print("Patched CinemetaWebViewPlayer statusBarsPadding successfully.")
else:
    print("Target not found in CinemetaWebViewPlayer")

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
