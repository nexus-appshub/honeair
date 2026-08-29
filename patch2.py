import sys

filepath = "app/src/main/java/com/example/ui/components/DownloaderModal.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix the regex escapes
content = content.replace('Regex("(?i)^(tt\d+|\d+)$")', 'Regex("(?i)^(tt\\\\d+|\\\\d+)$")')
content = content.replace('Regex("[\-_]")', 'Regex("[\\\\-_]")')
content = content.replace('Regex("(?i)\b(watch|online|free|download|full|hd|mp4|stream|streaming|1080p|720p|4k)\b")', 'Regex("(?i)\\\\b(watch|online|free|download|full|hd|mp4|stream|streaming|1080p|720p|4k)\\\\b")')
content = content.replace('Regex("\s+")', 'Regex("\\\\s+")')

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Escapes fixed")
