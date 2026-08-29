import re

with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'r') as f:
    content = f.read()

target = """                if (currentUrl.contains(".m3u8", ignoreCase = true) || 
                    currentUrl.contains("m3u8", ignoreCase = true) ||
                    (!currentUrl.endsWith(".mp4", ignoreCase = true) && !currentUrl.endsWith(".mkv", ignoreCase = true) && currentUrl.startsWith("http"))
                ) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                } else if (currentUrl.endsWith(".mp4", ignoreCase = true)) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP4)
                } else if (currentUrl.endsWith(".mkv", ignoreCase = true)) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MATROSKA)
                }"""

replacement = """                if (currentUrl.contains(".m3u8", ignoreCase = true) || 
                    currentUrl.contains("m3u8", ignoreCase = true)
                ) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/components/ExoPlayerView.kt', 'w') as f:
    f.write(content)
