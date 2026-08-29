import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

target = '''        // 2. Video Player Frame (Standard 16:9 ratio stream box)
        Box(
            modifier = if (isInPipMode) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            }
            .background(Color.Black)
        ) {'''

replacement = '''        // 2. Video Player Frame (Standard 16:9 ratio stream box)
        Box(
            modifier = if (isInPipMode || isFullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            }
            .background(Color.Black)
        ) {'''

if target in content:
    content = content.replace(target, replacement)
    print("Patched CinemetaWebViewPlayer Box size successfully.")
else:
    print("Target not found in CinemetaWebViewPlayer Box size")

# Also change SpaceBlack to Color.Black
target2 = 'Box(modifier = modifier.fillMaxSize().background(SpaceBlack)) {'
replacement2 = 'Box(modifier = modifier.fillMaxSize().background(Color.Black)) {'
if target2 in content:
    content = content.replace(target2, replacement2)
    print("Patched SpaceBlack to Color.Black successfully.")
else:
    print("Target2 not found in CinemetaWebViewPlayer")

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
