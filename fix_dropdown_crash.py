import re

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "r") as f:
    content = f.read()

# Let's locate the DropdownMenu block
target_start = """                        DropdownMenu(
                            expanded = showNotificationPopup,
                            onDismissRequest = { showNotificationPopup = false },
                            offset = androidx.compose.ui.unit.DpOffset(x = (-130).dp, y = 0.dp),
                            modifier = Modifier
                                .width(340.dp)
                                .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                                .border(1.dp, homeBorderColor, RoundedCornerShape(12.dp))
                        ) {"""

# We want to replace from DropdownMenu up to the closing braces of DropdownMenu
# Let's find the exact indices or do a regex/string replacement.

print("Target start in file:", content.find("expanded = showNotificationPopup"))
