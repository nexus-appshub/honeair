import re

with open('app/src/main/java/com/example/network/SmartNetworkBoosterEngine.kt', 'r') as f:
    content = f.read()

target = """            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Connection" to "Keep-Alive",
                    "Keep-Alive" to "timeout=60, max=1000"
                )
            )"""

replacement = """            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*"
                )
            )"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/network/SmartNetworkBoosterEngine.kt', 'w') as f:
    f.write(content)
