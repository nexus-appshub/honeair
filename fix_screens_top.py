with open("app/src/main/java/com/example/ui/screens/Screens.kt", "r") as f:
    lines = f.readlines()

new_lines = []
imports = []
for line in lines:
    if line.startswith("package "):
        new_lines.insert(0, line)
    else:
        new_lines.append(line)

with open("app/src/main/java/com/example/ui/screens/Screens.kt", "w") as f:
    f.writelines(new_lines)
