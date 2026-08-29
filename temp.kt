
                        Spacer(modifier = Modifier.height(10.dp))

                        // Banner Carousel Indicator Dots
                        val totalBannerItems = if (bannerMediaList.isNotEmpty()) bannerMediaList.size else 1
                        val coroutineScope = rememberCoroutineScope()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(totalBannerItems) { idx ->
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (idx == bannerIndex) 16.dp else 6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (idx == bannerIndex) Color(0xFFFF6B00) else Color(0xFFD1D1D6))
                                        .clickable { 
                                            coroutineScope.launch {
                                                val diff = idx - (pagerState.currentPage % totalBannerItems)
                                                pagerState.animateScrollToPage(pagerState.currentPage + diff)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // 2. EXPLORE CATEGORIES
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.example.ui.theme.AppTranslation.getString("explore_categories", selectedAudioIndex),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = homeTextColor
                            )

                            Text(
                                text = com.example.ui.theme.AppTranslation.getString("view_all", selectedAudioIndex),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B00),
                                modifier = Modifier.clickable {
                                    onNavigateToMediaTab("All")
                                    viewModel.setShowCategoriesGrid(true)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Horizontally Scrollable Category Row
                        val exploreCategoriesList = remember(selectedAudioIndex) {
                            listOf(
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("live_tv", selectedAudioIndex), Icons.Outlined.Tv) { showLiveTvBottomSheet = true },
                                HomeCategoryItem("All", Icons.Default.GridOn) { onNavigateToMediaTab("All") },
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("movies", selectedAudioIndex), Icons.Outlined.Movie) { onNavigateToMediaTab("Movies") },
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("tv_shows", selectedAudioIndex), Icons.Outlined.LiveTv) { onNavigateToMediaTab("Series & TV Shows") },
                                HomeCategoryItem("Anime", Icons.Outlined.LiveTv) { onNavigateToMediaTab("Anime") },
                                HomeCategoryItem("Bangla Cinema & Natok", Icons.Outlined.MovieFilter) { onNavigateToMediaTab("Bangla Cinema & Natok") },
                                HomeCategoryItem("Hindi Cinema", Icons.Outlined.Movie) { onNavigateToMediaTab("Hindi Cinema") },
                                HomeCategoryItem("Hindi Series", Icons.Outlined.Tv) { onNavigateToMediaTab("Hindi Series") },
                                HomeCategoryItem("Hindi Dubbed", Icons.Default.RecordVoiceOver) { onNavigateToMediaTab("Hindi Dubbed") },
                                HomeCategoryItem("Hindi Dubbed K-Dramas", Icons.Default.FavoriteBorder) { onNavigateToMediaTab("Hindi Dubbed K-Dramas") },
                                HomeCategoryItem("Anime Series", Icons.Outlined.SmartDisplay) { onNavigateToMediaTab("Anime Series") },
                                HomeCategoryItem("Anime Movies", Icons.Outlined.Theaters) { onNavigateToMediaTab("Anime Movies") },
                                HomeCategoryItem(com.example.ui.theme.AppTranslation.getString("k_drama", selectedAudioIndex), Icons.Outlined.OndemandVideo) { onNavigateToMediaTab("K-Dramas") },
                                HomeCategoryItem("Action", Icons.Default.Whatshot) { onNavigateToMediaTab("Action") },
                                HomeCategoryItem("Sci-Fi", Icons.Default.Public) { onNavigateToMediaTab("Sci-Fi") },
                                HomeCategoryItem("Sports", Icons.Outlined.SportsSoccer) { onNavigateToMediaTab("Sports") },
                                HomeCategoryItem("News", Icons.Outlined.Newspaper) { onNavigateToMediaTab("News") },
                                HomeCategoryItem("Kids", Icons.Outlined.ChildCare) { onNavigateToMediaTab("Kids") },
                                HomeCategoryItem("Music", Icons.Outlined.MusicNote) { onNavigateToMediaTab("Music") }
                            )
                        }

                        val categoryListState = rememberLazyListState()
                        val categoryScope = rememberCoroutineScope()
                        val totalCatPages = remember(exploreCategoriesList.size) {
                            (exploreCategoriesList.size + 1) / 2
                        }
                        val currentCatPage by remember(totalCatPages) {
                            derivedStateOf {
                                val firstIdx = categoryListState.firstVisibleItemIndex
                                (firstIdx / 2).coerceIn(0, totalCatPages - 1)
                            }
                        }

                        LazyRow(
                            state = categoryListState,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(exploreCategoriesList) { catItem ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { catItem.onClick() }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(homeCategoryBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catItem.icon,
                                            contentDescription = catItem.title,
                                            tint = homeCategoryIconTint,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = catItem.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = homeTextColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic Workable Category Dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            repeat(totalCatPages) { page ->
                                val isActive = page == currentCatPage
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (isActive) 16.dp else 6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isActive) Color(0xFFFF6B00) else if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6))
                                        .clickable {
                                            categoryScope.launch {
                                                categoryListState.animateScrollToItem((page * 2).coerceAtMost(exploreCategoriesList.size - 1))
                                            }
                                        }
                                )
                            }
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
                }

                // 3. Secret Pot Settings / Movie Picks Section (CONNECTED TO REAL TMDB DATA)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.example.ui.theme.AppTranslation.getString("secret_pot_settings", selectedAudioIndex),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = homeTextColor
                            )

                            Text(
                                text = "View All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B00),
                                modifier = Modifier.clickable { onNavigateToMediaTab("Movies") }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (realMediaList.isEmpty()) {
                            ShimmerPosterRow(itemCount = 4)
                        } else {
                            val listState = rememberLazyListState()
                            LaunchedEffect(realMediaList) {
                                while (true) {
                                    kotlinx.coroutines.delay(4000)
                                    val currentItem = listState.firstVisibleItemIndex
                                    val nextItem = if (currentItem < realMediaList.size - 1) currentItem + 1 else 0
                                    listState.animateScrollToItem(nextItem)
                                }
                            }
                            LazyRow(
                                state = listState,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(realMediaList) { mediaItem ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF1C1C1E),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(150.dp)
                                            .clickable {
                                                viewModel.playMediaItem(mediaItem, 1, 1)
                                                        onNavigateToPlayer()
                                            }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Real Poster Image
                                            if (mediaItem.imageUrl.isNotEmpty()) {
                                                AsyncImage(
                                                    model = mediaItem.imageUrl,
                                                    contentDescription = mediaItem.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Developer Footer
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    DeveloperNoteFooter()
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            }
        } else {
            // VIEW 2: IPTV NODES & PLAYLIST / CHANNEL BROWSER

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Category Filter Row
        if (selectedPlaylist == null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategoryFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (category == "Fixed Channels") {
                                val fixedPlaylist = filteredPlaylists.find { it.name.contains("Fixed Channels", ignoreCase = true) }
                                if (fixedPlaylist != null) {
                                    viewModel.selectPlaylist(fixedPlaylist)
                                }
                            } else {
                                selectedCategoryFilter = category
                            }
                        },
                        label = {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple,
                            selectedLabelColor = Color.White,
                            containerColor = DeepSlate,
                            labelColor = TextPrimary
                        ),
