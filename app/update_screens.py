import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update HomeScreen Header for Universal Search
old_home_header_content = """        // Main App Header Row (Branding on Left; Search, Bell & Sign In on Right)
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + expandVertically(expandFrom = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeAirTvBrandingHeader(
                    selectedPlaylistName = selectedPlaylist?.name,
                    modifier = Modifier.weight(1f, fill = false),
                    clickTrigger = screenClickCount
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isIptvView = isLiveTvViewMode || selectedPlaylist != null
                    if (isIptvView) {
                        IconButton(
                            onClick = { 
                                isIptvSearchActive = !isIptvSearchActive 
                                if (!isIptvSearchActive) {
                                    viewModel.setPlaylistSearchQuery("")
                                    viewModel.setChannelSearchQuery("")
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isIptvSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = homeIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        // Notification Bell Button with Red Badge"""

new_home_header_content = """        var isUniversalSearchActive by remember { mutableStateOf(false) }
        var universalSearchQuery by remember { mutableStateOf("") }

        // Main App Header Row (Branding on Left; Search, Bell & Sign In on Right)
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + expandVertically(expandFrom = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
        ) {
            AnimatedContent(
                targetState = isUniversalSearchActive,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + expandHorizontally())
                        .togetherWith(fadeOut(animationSpec = tween(180)) + shrinkHorizontally())
                },
                label = "UniversalSearchHeaderAnimation"
            ) { searchActive ->
                if (searchActive) {
                    // Expanded Search Bar connected directly to Movie & Anime Database with Fuzzy Matching
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = homeCardBg,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isUniversalSearchActive = false
                                    universalSearchQuery = ""
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = universalSearchQuery,
                                onValueChange = { universalSearchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = homeTextColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(NeonCyan),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (universalSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Search movies, anime, series, live TV...",
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = homeSubTextColor,
                                                fontSize = 13.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (universalSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { universalSearchQuery = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = homeSubTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeAirTvBrandingHeader(
                            selectedPlaylistName = selectedPlaylist?.name,
                            modifier = Modifier.weight(1f, fill = false),
                            clickTrigger = screenClickCount
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val isIptvView = isLiveTvViewMode || selectedPlaylist != null
                            if (isIptvView) {
                                IconButton(
                                    onClick = { 
                                        isIptvSearchActive = !isIptvSearchActive 
                                        if (!isIptvSearchActive) {
                                            viewModel.setPlaylistSearchQuery("")
                                            viewModel.setChannelSearchQuery("")
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isIptvSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = homeIconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                // Universal Search Icon Button (Connected to Movies/Anime Hub Database)
                                IconButton(
                                    onClick = { isUniversalSearchActive = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Universal Search",
                                        tint = homeIconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Notification Bell Button with Red Badge"""

if old_home_header_content in content:
    content = content.replace(old_home_header_content, new_home_header_content)
    print('1. Updated HomeScreen header with Universal Search!')
else:
    print('1. Error: old_home_header_content not found')

# 2. Add Universal Search Results Overlay in HomeScreen
search_overlay_target = 'val isIptvView = isLiveTvViewMode || selectedPlaylist != null'
search_overlay_replacement = '''val isIptvView = isLiveTvViewMode || selectedPlaylist != null

        // Universal Fuzzy Search Results Overlay
        AnimatedVisibility(
            visible = isUniversalSearchActive && universalSearchQuery.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val fuzzyResults = remember(universalSearchQuery, realMediaList) {
                viewModel.fuzzySearchMedia(universalSearchQuery, realMediaList)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = homeCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔎 Found ${fuzzyResults.size} matches for \\"${universalSearchQuery}\\"",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (fuzzyResults.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No titles found. Try checking the spelling.", color = homeSubTextColor, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(fuzzyResults, key = { "search_" + it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedItemForDetail = item
                                            isUniversalSearchActive = false
                                        }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp, 56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DeepSlate)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = homeTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.category} • ${item.year.ifEmpty { "HD" }} • ★ ${item.rating}",
                                            fontSize = 11.sp,
                                            color = homeSubTextColor
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.playMediaItem(item, 1, 1)
                                            isUniversalSearchActive = false
                                            onNavigateToPlayer()
                                        },
                                        modifier = Modifier.size(32.dp).background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
'''

if search_overlay_target in content:
    content = content.replace(search_overlay_target, search_overlay_replacement, 1)
    print('2. Added Universal Search Overlay in HomeScreen!')
else:
    print('2. Error: search_overlay_target not found')

# 3. Rename Explore categories button: "Latest Releases" -> "Latest"
old_cat_latest = 'HomeCategoryItem("Latest Releases", Icons.Default.AutoAwesome) { onNavigateToMediaTab("Latest Releases") },'
new_cat_latest = 'HomeCategoryItem("Latest", Icons.Default.AutoAwesome) { onNavigateToMediaTab("Latest Releases") },'
if old_cat_latest in content:
    content = content.replace(old_cat_latest, new_cat_latest)
    print('3. Renamed Latest Releases button to Latest!')
else:
    print('3. Error: old_cat_latest not found')

# 4. Move Continue Watching above Latest Releases in HomeScreen
old_home_rows = """                // ✨ Latest Releases Section (Movies, Series & Anime synced regularly)
                val displayLatestList = if (latestReleases.isNotEmpty()) latestReleases else realMediaList.take(15)
                if (displayLatestList.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MediaCategoryRowSection(
                                title = "✨ Latest Releases",
                                items = displayLatestList,
                                onSeeAllClick = { onNavigateToMediaTab("Latest Releases") },
                                onItemClick = { selectedItemForDetail = it },
                                onPlayClick = { item ->
                                    viewModel.playMediaItem(item, 1, 1)
                                    onNavigateToPlayer()
                                },
                                onLoadMore = { viewModel.loadMoreMedia() }
                            )
                        }
                    }
                }

                if (mediaWatchHistory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MediaCategoryRowSection(
                                title = "🕒 Continue Watching",
                                items = mediaWatchHistory.take(15),
                                onSeeAllClick = { onNavigateToMediaTab("All") },
                                onItemClick = { selectedItemForDetail = it },
                                onPlayClick = { item ->
                                    viewModel.playMediaItem(item)
                                    onNavigateToPlayer()
                                }
                            )
                        }
                    }
                }"""

new_home_rows = """                // 🕒 Continue Watching Section (Placed above Latest Releases with sleek progress bar)
                if (mediaWatchHistory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ContinueWatchingRowSection(
                                title = "🕒 Continue Watching",
                                items = mediaWatchHistory.take(15),
                                viewModel = viewModel,
                                onSeeAllClick = { onNavigateToMediaTab("All") },
                                onItemClick = { selectedItemForDetail = it },
                                onPlayClick = { item ->
                                    onNavigateToPlayer()
                                }
                            )
                        }
                    }
                }

                // ✨ Latest Releases Section (Movies, Series & Anime synced regularly)
                val displayLatestList = if (latestReleases.isNotEmpty()) latestReleases else realMediaList.take(15)
                if (displayLatestList.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MediaCategoryRowSection(
                                title = "✨ Latest Releases",
                                items = displayLatestList,
                                onSeeAllClick = { onNavigateToMediaTab("Latest Releases") },
                                onItemClick = { selectedItemForDetail = it },
                                onPlayClick = { item ->
                                    viewModel.playMediaItem(item, 1, 1)
                                    onNavigateToPlayer()
                                },
                                onLoadMore = { viewModel.loadMoreMedia() }
                            )
                        }
                    }
                }"""

if old_home_rows in content:
    content = content.replace(old_home_rows, new_home_rows)
    print('4. Moved Continue Watching above Latest Releases with custom card in HomeScreen!')
else:
    print('4. Error: old_home_rows not found')

# 5. MediaHubScreen: Add statusBarsPadding
old_mediahub_col = """    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {"""

new_mediahub_col = """    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .statusBarsPadding()
        ) {"""

if old_mediahub_col in content:
    content = content.replace(old_mediahub_col, new_mediahub_col)
    print('5. Added statusBarsPadding to MediaHubScreen!')
else:
    print('5. Error: old_mediahub_col not found')

# 6. MediaHubScreen: Explore Categories -> Categories
content = content.replace('text = "Explore Categories"', 'text = "Categories"')
print('6. Updated Explore Categories chips to Categories')

# 7. MediaHubScreen: Move Featured Banner above Latest Releases and all rows, set edgeToEdge = true
old_mediahub_all = """                                 else -> { // "All"
                                     val displayLatest = if (latestReleases.isNotEmpty()) latestReleases else allItems.take(15)
                                     if (displayLatest.isNotEmpty()) {
                                         item {
                                             MediaCategoryRowSection(
                                                 title = "✨ Latest Releases",
                                                 items = displayLatest,
                                                 onSeeAllClick = { viewModel.setSelectedMediaCategory("Latest Releases") },
                                                 onItemClick = { selectedItemForDetail = it },
                                                 onPlayClick = { item ->
                                                     viewModel.playMediaItem(item, 1, 1)
                                                     onNavigateToPlayer()
                                                 },
                                                 onLoadMore = { viewModel.loadMoreMedia() }
                                             )
                                         }
                                     }

                                     if (popularList.isNotEmpty()) {
                                         item {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             com.example.ui.components.AutoScrollingBannerCarousel(
                                                 items = popularList,
                                                 onItemClick = { selectedItemForDetail = it }
                                             )
                                         }
                                     }"""

new_mediahub_all = """                                 else -> { // "All"
                                     // Featured Banner moved above all rows, left to right edge-to-edge
                                     if (popularList.isNotEmpty()) {
                                         item {
                                             com.example.ui.components.AutoScrollingBannerCarousel(
                                                 items = popularList,
                                                 onItemClick = { selectedItemForDetail = it },
                                                 edgeToEdge = true
                                             )
                                             Spacer(modifier = Modifier.height(8.dp))
                                         }
                                     }

                                     val displayLatest = if (latestReleases.isNotEmpty()) latestReleases else allItems.take(15)
                                     if (displayLatest.isNotEmpty()) {
                                         item {
                                             MediaCategoryRowSection(
                                                 title = "✨ Latest Releases",
                                                 items = displayLatest,
                                                 onSeeAllClick = { viewModel.setSelectedMediaCategory("Latest Releases") },
                                                 onItemClick = { selectedItemForDetail = it },
                                                 onPlayClick = { item ->
                                                     viewModel.playMediaItem(item, 1, 1)
                                                     onNavigateToPlayer()
                                                 },
                                                 onLoadMore = { viewModel.loadMoreMedia() }
                                             )
                                         }
                                     }"""

if old_mediahub_all in content:
    content = content.replace(old_mediahub_all, new_mediahub_all)
    print('7. Updated MediaHubScreen All tab with edge-to-edge banner at the top!')
else:
    print('7. Error: old_mediahub_all not found')

# 8. SettingsScreen: Remove top search bar
old_settings_search = """        // 1. Top Search Bar (Matching Screenshot)
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + expandVertically(expandFrom = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = settingsSearchBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = settingsSubTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) "Search" else searchQuery,
                            fontSize = 14.sp,
                            color = if (searchQuery.isEmpty()) settingsSubTextColor else settingsTextColor
                        )
                    }
                }
            }
        }"""

if old_settings_search in content:
    content = content.replace(old_settings_search, '// Top Search Bar removed as requested')
    print('8. Removed top search bar from SettingsScreen!')
else:
    print('8. Error: old_settings_search not found')

# 9. Update WatchHistorySheet with watched progress, remaining time, start again (replay) button, and resume
old_watch_history_item = """                                    IconButton(
                                        onClick = {
                                            viewModel.playMediaItem(item)
                                            onDismiss()
                                            onNavigateToPlayer()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }"""

new_watch_history_item = """                                    val progressInfo = remember(item.id, item.imdbId) {
                                        viewModel.getMediaPlaybackProgress(item.imdbId ?: item.id)
                                    }
                                    val posMs = progressInfo.first
                                    val durMs = progressInfo.second
                                    val progressRatio = if (durMs > 0) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0.45f
                                    val formatTime: (Long) -> String = { ms ->
                                        val totalSec = (ms / 1000).coerceAtLeast(0)
                                        val s = totalSec % 60
                                        val m = (totalSec / 60) % 60
                                        val h = totalSec / 3600
                                        if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
                                    }

                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // Start Again Icon Button
                                            IconButton(
                                                onClick = {
                                                    viewModel.saveMediaPlaybackProgress(item.imdbId ?: item.id, 0L, durMs)
                                                    viewModel.playMediaItem(item, 1, 1, startPositionMs = 0L)
                                                    onDismiss()
                                                    onNavigateToPlayer()
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Replay,
                                                    contentDescription = "Start Again",
                                                    tint = homeSubTextColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Resume Button
                                            IconButton(
                                                onClick = {
                                                    viewModel.playMediaItem(item, 1, 1, startPositionMs = posMs)
                                                    onDismiss()
                                                    onNavigateToPlayer()
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(NeonCyan.copy(alpha = 0.2f), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Resume",
                                                    tint = NeonCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        if (durMs > 0) {
                                            Text(
                                                text = "${formatTime(posMs)} / ${formatTime(durMs)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = NeonCyan
                                            )
                                        }
                                    }"""

if old_watch_history_item in content:
    content = content.replace(old_watch_history_item, new_watch_history_item)
    print('9. Updated WatchHistorySheet with progress, time, and Start Again replay button!')
else:
    print('9. Error: old_watch_history_item not found')

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print('Screens.kt updated successfully!')
