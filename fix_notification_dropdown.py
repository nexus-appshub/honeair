import re

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "r") as f:
    content = f.read()

# Let's find the DropdownMenu block and replace it with a robust scrollable Column version
# We can search from DropdownMenu( to the closing of DropdownMenu.
# Let's inspect around line 1160 to 1290.

start_idx = content.find("DropdownMenu(\n                            expanded = showNotificationPopup")
if start_idx == -1:
    start_idx = content.find("DropdownMenu(")

print("Start index:", start_idx)

# Let's write a targeted replacement
