import re

with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'r') as f:
    content = f.read()

target = '''            ) {
                // Top Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(16.dp),'''

replacement = '''            ) {
                // Top Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp),'''

if target in content:
    content = content.replace(target, replacement)
    print("Patched ExoPlayerView Top Toolbar successfully.")
else:
    print("Target not found in ExoPlayerView Top Toolbar")

target2 = '''                // Bottom Timeline & Seek Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = if (isFullScreen) 40.dp else 12.dp),'''

replacement2 = '''                // Bottom Timeline & Seek Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = if (isFullScreen) 40.dp else 12.dp),'''

if target2 in content:
    content = content.replace(target2, replacement2)
    print("Patched ExoPlayerView Bottom Timeline successfully.")
else:
    print("Target not found in ExoPlayerView Bottom Timeline")
    
with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'w') as f:
    f.write(content)
