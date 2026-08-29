import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

target = "padding(if (isFullScreen || isInPipMode || selectedTabIndex == 2) PaddingValues(0.dp) else innerPadding)"
replacement = "padding(if (isFullScreen || isInPipMode) PaddingValues(0.dp) else if (selectedTabIndex == 2) PaddingValues(bottom = innerPadding.calculateBottomPadding()) else innerPadding)"

if target in content:
    content = content.replace(target, replacement)
    print("Patched MainActivity.kt bottom padding successfully.")
else:
    print("Target not found in MainActivity.kt")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
