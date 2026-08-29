import sys

filepath = "app/src/main/java/com/example/ui/components/DownloaderModal.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace('Regex("(?i)videodownloader")', 'Regex("(?i)video\\\\s*downloader")')
content = content.replace('Regex("(?i)02moviedownloader")', 'Regex("(?i)02\\\\s*movie\\\\s*downloader")')

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Regex fixed")
