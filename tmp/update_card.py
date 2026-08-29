import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r', encoding='utf-8') as f:
    text = f.read()

pos = text.find('fun ContinueWatchingCard')
end_pos = text.find('fun ContinueWatchingRowSection')

if pos != -1 and end_pos != -1:
    old_block = text[pos:end_pos]
    
    new_block = """fun ContinueWatchingCard(
    item: MediaItem,
    viewModel: StreamViewModel,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cwCardScale"
    )
    val progressInfo = remember(item.id, item.imdbId) {
        viewModel.getMediaPlaybackProgress(item.imdbId ?: item.id)
    }
    val posMs = progressInfo.first
    val durMs = progressInfo.second
    val progressRatio = if (durMs > 0) (posMs.toFloat() / durMs.toFloat()).coerceIn(0.05f, 1f) else 0.45f
    val progressPercent = (progressRatio * 100).toInt()

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isPressed) NeonCyan else BorderColor),
        modifier = modifier
            .width(200.dp)
            .height(115.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    coroutineScope.launch {
                        delay(15)
                        onClick()
                    }
                }
            )
            .testTag("continue_watching_card_${item.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var isImageLoadError by remember(item.imageUrl) { mutableStateOf(false) }
            
            // Thumbnail image background
            if (!isImageLoadError && item.imageUrl.isNotBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(item.imageUrl) {
                    ImageRequest.Builder(context)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .diskCacheKey(item.imageUrl)
                        .memoryCacheKey(item.imageUrl)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    onError = { isImageLoadError = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isImageLoadError || item.imageUrl.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2C1A30), Color(0xFF111827))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = LightAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Beautiful Gradient Scrim Overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            // Content Overlay (Title, Info & Progress percentage badge)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row with IMDb rating or Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small Category badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = item.category,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Rating Badge
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(8.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = item.rating,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom section (Title + watched percent / label status)
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.year.ifEmpty { "HD" },
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        
                        // User progress label / status (e.g. "Completed" or "75% Watched")
                        Surface(
                            color = if (progressPercent >= 95) Color(0xFF2E7D32) else NeonCyan.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (progressPercent >= 95) "Watched" else f"{progressPercent}% Watched",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Progress Line along card's bottom edge
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = if (progressPercent >= 95) Color(0xFF4CAF50) else NeonCyan,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

"""
    text = text.replace(old_block, new_block)
    with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w', encoding='utf-8') as f:
        f.write(text)
    print("SUCCESS")
else:
    print("FAILED TO LOCATE")
