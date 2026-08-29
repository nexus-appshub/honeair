import sys

filepath = "app/src/main/java/com/example/ui/components/DownloaderModal.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

replacement = """
            // Try to extract a clean slug from URL if possible
            var slugTitle = ""
            try {
                val uri = android.net.Uri.parse(currentUrl)
                val paths = uri.pathSegments
                if (!paths.isNullOrEmpty()) {
                    val validSegments = paths.filter { 
                        it.length > 2 && 
                        !it.matches(Regex("(?i)^(tt\\d+|\\d+)$")) && // ignore imdb ids or purely numeric
                        !listOf("movie", "tv", "watch", "episode", "season", "series", "stream", "download", "embed").contains(it.lowercase())
                    }
                    if (validSegments.isNotEmpty()) {
                        slugTitle = validSegments.last().replace(Regex("[\\-_]"), " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(java.util.Locale.getDefault()) else c.toString() } }
                    }
                }
            } catch (e: Exception) {}

            // Get page title for manual downloads if needed
            val pageTitle = webViewRef?.title
            val isHashOrUrl = pageTitle != null && (pageTitle.matches(Regex(".*[a-fA-F0-9]{16,}.*")) || pageTitle.contains("http") || pageTitle.contains(".mp4") || pageTitle.contains(".m3u8"))
            
            var baseTitle = if (title == "HD Search") {
                if (slugTitle.isNotBlank()) {
                    slugTitle
                } else if (!pageTitle.isNullOrBlank() && !isHashOrUrl) {
                    var cleaned = pageTitle
                    
                    // Grab the part before common separators like |, -, :
                    val sepIndex = cleaned.indexOfFirst { it == '|' || it == '-' || it == ':' || it == '–' || it == '—' }
                    if (sepIndex > 0) {
                        cleaned = cleaned.substring(0, sepIndex)
                    }

                    cleaned = cleaned
                        .replace(Regex("(?i)\\b(watch|online|free|download|full|hd|mp4|stream|streaming|1080p|720p|4k)\\b"), "")
                        .replace(Regex("(?i)videodownloader"), "")
                        .replace(Regex("(?i)02moviedownloader"), "")
                        .replace(Regex("(?i)omnisandbox"), "")
                        .replace(Regex("(?i)site"), "")
                        .replace(Regex("[^A-Za-z0-9 ]"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        
                    if (cleaned.isBlank() || cleaned.length < 2) "Media_Download" else cleaned
                } else {
                    "Media_Download"
                }
            } else {
                title
            }
"""

old_code = """
            // Get page title for manual downloads if needed
            val pageTitle = webViewRef?.title
            val isHashOrUrl = pageTitle != null && (pageTitle.matches(Regex(".*[a-fA-F0-9]{16,}.*")) || pageTitle.contains("http") || pageTitle.contains(".mp4") || pageTitle.contains(".m3u8"))
            var baseTitle = if (title == "HD Search" && !pageTitle.isNullOrBlank() && !isHashOrUrl) {
                var cleaned = pageTitle
                    .replace(Regex("(?i)watch"), "")
                    .replace(Regex("(?i)online"), "")
                    .replace(Regex("(?i)free"), "")
                    .replace(Regex("(?i)download"), "")
                    .replace(Regex("(?i)videodownloader"), "")
                    .replace(Regex("(?i)02moviedownloader"), "")
                    .replace(Regex("(?i)site"), "")
                    .replace(Regex("[|:\\-_]"), " ")
                    .trim()
                if (cleaned.isBlank()) "Media_Download" else cleaned
            } else if (title == "HD Search") {
                "Media_Download"
            } else {
                title
            }
"""

if old_code.strip() in content:
    content = content.replace(old_code.strip(), replacement.strip())
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Failed to find block.")
    # Fallback line-by-line replace
    lines = content.splitlines()
    for i, line in enumerate(lines):
        if "val pageTitle = webViewRef?.title" in line:
            print("Found starting line at", i)
            end_idx = i
            while end_idx < len(lines) and "} else {" not in lines[end_idx] or "title" not in lines[end_idx+1]:
                end_idx += 1
            end_idx += 2 # include '} else { title }'
            print("End line at", end_idx)
            
            indent = line[:line.index("val pageTitle")]
            new_lines = [indent + l for l in replacement.strip().splitlines()]
            
            lines[i:end_idx+1] = new_lines
            with open(filepath, "w", encoding="utf-8") as f:
                f.write("\n".join(lines))
            print("Replaced using line indices.")
            break
