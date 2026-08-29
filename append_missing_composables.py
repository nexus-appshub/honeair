import os

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r', encoding='utf-8') as f:
    text = f.read()

pos = text.find('@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun MediaDetailSheet(')
if pos == -1:
    pos = text.find('fun MediaDetailSheet(')

if pos != -1:
    clean_text = text[:pos]
else:
    print('ERROR: MediaDetailSheet not found')
    exit(1)

new_code = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailSheet(
    item: MediaItem,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onDismiss: () -> Unit,
    onPlayStream: (season: Int, episode: Int) -> Unit,
    viewModel: StreamViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSeries = item.type == "series" || item.category in listOf("Series & TV Shows", "Anime", "Anime Series", "Anime Movies", "K-Dramas", "Hindi Series")
    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var showDownloaderModal by remember { mutableStateOf(false) }

    val castMembers by viewModel.castState.collectAsState()
    val isFetchingCast by viewModel.isFetchingCast.collectAsState()
    val mediaDetails by viewModel.mediaDetailState.collectAsState()

    LaunchedEffect(item.id) {
        viewModel.fetchMediaDetails(item.imdbId ?: item.id, item.type)
    }

    val totalSeasons = remember(mediaDetails) {
        val num = mediaDetails?.number_of_seasons
        if (num != null && num > 0) num else 10
    }
    val totalEpisodes = remember(mediaDetails) {
        val num = mediaDetails?.number_of_episodes
        if (num != null && num > 0) num else 30
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Hero / Banner Image
            val imageUrl = item.imageUrl
            if (!imageUrl.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2C2C2E))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF1C1C1E)),
                                    startY = 100f
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Title & Year & Category
            Text(
                text = item.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.year.isNotBlank()) {
                    Surface(
                        color = NeonPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.year,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (item.rating.isNotBlank()) {
                    Surface(
                        color = Color(0xFFFFD700).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.rating,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                }
                if (item.category.isNotBlank()) {
                    Text(
                        text = item.category,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Overview / Description
            val overviewText = mediaDetails?.overview ?: item.description
            if (!overviewText.isBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = overviewText,
                    fontSize = 13.sp,
                    color = Color.LightGray.copy(alpha = 0.9f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Series Season & Episode Picker
            if (isSeries) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                // Season Selection Header & Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Season",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Season $selectedSeason Selected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonCyan
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..maxOf(1, totalSeasons)).toList()) { s ->
                        val isSelected = selectedSeason == s
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSeason = s
                                selectedEpisode = 1
                            },
                            label = {
                                Text(
                                    text = "Season $s",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF2C2C2E),
                                labelColor = Color.LightGray
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.2f),
                                selectedBorderColor = NeonCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Episode Selection Header & Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Episode $selectedEpisode Selected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonCyan
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val epList = (1..maxOf(1, totalEpisodes)).toList()
                val hasManyEps = epList.size > 25
                val epChunkSize = 25
                val epRanges = remember(epList.size) {
                    if (hasManyEps) epList.chunked(epChunkSize).map { it.first()..it.last() } else emptyList()
                }
                var activeEpRangeIdx by remember(selectedSeason, epList.size) {
                    mutableIntStateOf(0)
                }

                if (hasManyEps && epRanges.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        itemsIndexed(epRanges) { idx, range ->
                            val isSelectedRange = activeEpRangeIdx == idx
                            FilterChip(
                                selected = isSelectedRange,
                                onClick = { activeEpRangeIdx = idx },
                                label = { Text("${range.first}-${range.last}", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.3f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = Color(0xFF252528),
                                    labelColor = Color.Gray
                                )
                            )
                        }
                    }
                }

                val visibleEpisodes = remember(activeEpRangeIdx, epRanges, epList) {
                    if (hasManyEps && epRanges.isNotEmpty() && activeEpRangeIdx in epRanges.indices) {
                        val r = epRanges[activeEpRangeIdx]
                        epList.filter { it in r.first..r.last }
                    } else {
                        epList
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleEpisodes) { ep ->
                        val isSelected = selectedEpisode == ep
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedEpisode = ep },
                            label = {
                                Text(
                                    text = "Ep $ep",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF2C2C2E),
                                labelColor = Color.LightGray
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.2f),
                                selectedBorderColor = NeonCyan
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Action Buttons Row (Play, Favorite, Download)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onPlayStream(selectedSeason, selectedEpisode)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSeries) "Play S$selectedSeason E$selectedEpisode" else "Watch Stream",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else Color.White
                    )
                }

                IconButton(
                    onClick = { showDownloaderModal = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = NeonCyan
                    )
                }
            }

            // Cast section if available
            if (castMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Cast & Crew",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(castMembers) { cast ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(72.dp)
                        ) {
                            val profileUrl = cast.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
                            if (!profileUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profileUrl,
                                    contentDescription = cast.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3A3A3C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cast.name ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDownloaderModal) {
        val userProfile by viewModel.userProfile.collectAsState()
        com.example.ui.components.DownloaderModal(
            showModal = showDownloaderModal,
            onDismiss = { showDownloaderModal = false },
            title = item.title,
            imdbId = item.imdbId ?: item.id,
            season = selectedSeason,
            episode = selectedEpisode,
            isSeries = isSeries,
            coroutineScope = scope,
            userProfile = userProfile,
            viewModel = viewModel
        )
    }
}

@Composable
fun RealGoogleAccountsDialog(
    accounts: List<String>,
    onDismiss: () -> Unit,
    onAccountSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Google Account", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    Surface(
                        onClick = { onAccountSelected(account) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2C2C2E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(account, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1C1C1E)
    )
}

@Composable
fun ShimmerPosterRow(
    itemCount: Int = 4,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(itemCount) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2E))
            )
        }
    }
}

@Composable
fun ShimmerPosterGrid(
    columns: Int = 2,
    itemCount: Int = 6,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(itemCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2E))
            )
        }
    }
}

@Composable
fun WebBrowserDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Web Version", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text("Do you want to open Home Air TV in your external browser?", color = Color.LightGray) },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Text("Open Browser", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1C1C1E)
    )
}

@Composable
fun PrivacyPolicySectionCard(
    title: String,
    icon: ImageVector,
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = text, fontSize = 13.sp, color = Color.LightGray, lineHeight = 18.sp)
        }
    }
}
"""

full_content = clean_text + new_code

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w', encoding='utf-8') as f:
    f.write(full_content)

print('Screens.kt updated with all missing composables. Total lines:', full_content.count('\n'))
