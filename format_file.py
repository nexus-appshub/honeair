with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    lines = f.readlines()

out_lines = []
skip = False

for i, line in enumerate(lines):
    if "// Floating Download Button" in line:
        out_lines.append(line)
        # we will handle the next few lines explicitly
        # until @Composable
        skip = True
        
        fab_code = """        if (capturedVideoUrl != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                        val suffix = if (isSeries) "_S${currentSeason}_E${currentEpisode}" else ""
                        val extension = if (capturedVideoUrl!!.lowercase().contains(".m3u8")) "mp4" else "mp4"
                        val fileName = "${safeTitle}${suffix}.$extension"
                        
                        MediaDownloader.downloadFile(
                            context = context,
                            url = capturedVideoUrl!!,
                            fileName = fileName,
                            coroutineScope = scope
                        )
                    },
                    containerColor = NeonPurple,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 100.dp)
                        .size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download"
                    )
                }
            }
        }
    }
}
"""
        out_lines.append(fab_code)
    elif skip and "@Composable" in line:
        skip = False
        out_lines.append("\n")
        out_lines.append(line)
    elif not skip:
        out_lines.append(line)

# remove trailing closing braces at the end of the file
while out_lines[-1].strip() == "}":
    out_lines.pop()

out_lines.append("}\n")
out_lines.append("}\n")

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.writelines(out_lines)
