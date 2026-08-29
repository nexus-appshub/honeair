import re
with open("app/src/main/java/com/example/update/AppUpdateManager.kt", "r") as f:
    content = f.read()

# count { and }
print("Opening { :", content.count("{"))
print("Closing } :", content.count("}"))
