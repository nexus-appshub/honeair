import re

with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'r') as f:
    content = f.read()

# 1. Center controls spacing
old_center = 'horizontalArrangement = Arrangement.spacedBy(if (isFullScreen) 28.dp else 16.dp),'
new_center = 'horizontalArrangement = Arrangement.spacedBy(if (isFullScreen) 36.dp else 22.dp),'

if old_center in content:
    content = content.replace(old_center, new_center)
    print("Updated center controls spacing")
else:
    print("old_center not found")

# 2. Bottom timeline padding & spacing
old_bottom = '.padding(bottom = if (isFullScreen) 40.dp else 12.dp),\n                    verticalArrangement = Arrangement.spacedBy(2.dp)'
new_bottom = '.padding(bottom = if (isFullScreen) 50.dp else 24.dp),\n                    verticalArrangement = Arrangement.spacedBy(6.dp)'

if old_bottom in content:
    content = content.replace(old_bottom, new_bottom)
    print("Updated bottom timeline padding and spacing")
else:
    print("old_bottom not found")

with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'w') as f:
    f.write(content)

print("Updated ExoPlayerView successfully.")
