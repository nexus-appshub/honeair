import re

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'r') as f:
    content = f.read()

# Fix header padding
target_header = """            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 10.dp)
            ) {"""

replacement_header = """            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 12.dp)
            ) {"""

content = content.replace(target_header, replacement_header)

# Fix player frame padding
target_player = """        Box(
            modifier = if (isInPipMode) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
            }"""

replacement_player = """        Box(
            modifier = if (isInPipMode) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            }"""

content = content.replace(target_player, replacement_player)

with open('app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt', 'w') as f:
    f.write(content)
