with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

download_block = """            // High-speed downloader from intercepted stream URLs
            if (capturedVideoUrl != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
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
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }"""

if download_block in content:
    content = content.replace(download_block, "")
else:
    print("Download block not found!")

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.write(content)
