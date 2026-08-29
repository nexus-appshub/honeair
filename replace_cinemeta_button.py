import re

with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "r") as f:
    content = f.read()

target = """        // Floating Download Button
        if (capturedVideoUrl != null) {
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
        }"""

replacement = """        // Floating Download Button
        if (capturedVideoUrl != null) {
            val activeDownloads by MediaDownloader.activeDownloads.collectAsState()
            val downloadId = capturedVideoUrl!!.hashCode().toString()
            val activeDownload = activeDownloads.find { it.id == downloadId }
            val isDownloading = activeDownload != null && !activeDownload.isCancelled

            Box(modifier = Modifier.fillMaxSize()) {
                if (isDownloading) {
                    val progress = activeDownload?.progress ?: 0
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress / 100f,
                        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                        label = "progress"
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 100.dp)
                            .size(56.dp)
                            .shadow(8.dp, CircleShape, spotColor = NeonCyan)
                            .clip(CircleShape)
                            .background(com.example.ui.theme.DeepSlate)
                            .clickable { MediaDownloader.cancelDownload(downloadId) },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 3.dp.toPx()
                            drawArc(
                                color = Color.DarkGray,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                            drawArc(
                                color = NeonCyan,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${progress}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
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
        }"""

new_content = content.replace(target, replacement)
if new_content == content:
    print("NO MATCH FOUND")
else:
    with open("app/src/main/java/com/example/ui/components/CinemetaWebViewPlayer.kt", "w") as f:
        f.write(new_content)
    print("REPLACED SUCCESSFULLY")
