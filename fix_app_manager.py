import re

with open("app/src/main/java/com/example/update/AppUpdateManager.kt", "r") as f:
    content = f.read()

content = content.replace("/**\n     * tallApk", "/**\n     * Triggers native Android Package Installer for the downloaded APK file.\n     */\n    fun installApk")

with open("app/src/main/java/com/example/update/AppUpdateManager.kt", "w") as f:
    f.write(content)
