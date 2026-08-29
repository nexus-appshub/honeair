with open("app/src/main/java/com/example/ui/screens/Screens.kt", "r") as f:
    lines = f.readlines()

new_lines = []
new_lines.append("package com.example.ui.screens\n")
for line in lines:
    if not line.startswith("package com.example.ui.screens"):
        new_lines.append(line)

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "w") as f:
    f.writelines(new_lines)
