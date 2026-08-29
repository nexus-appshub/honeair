with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

end_block = """                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    }
}"""

new_end_block = """                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        // Floating Download Button
        if (capturedVideoUrl != null) {
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
}"""

if end_block in content:
    content = content.replace(end_block, new_end_block)
else:
    print("End block not found!")

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
    f.write(content)
