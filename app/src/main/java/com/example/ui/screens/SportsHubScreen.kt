package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.IptvChannel
import com.example.data.model.LiveMatch
import com.example.data.model.SportChannel
import com.example.data.model.StreamServer
import com.example.ui.viewmodel.StreamViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch

@Composable
fun SportsHubScreen(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Live Events, 1: Sports Channels
    val sportsEventsState by viewModel.sportsEventsState.collectAsState()
    val sportsChannelsState by viewModel.sportsChannelsState.collectAsState()
    val isDark = isSystemInDarkTheme()

    val bgColor = if (isDark) Color(0xFF09090B) else Color(0xFFF4F4F5)
    val cardBgColor = if (isDark) Color(0xFF18181B) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF18181B)
    val subTextColor = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val borderColor = if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)

    LaunchedEffect(Unit) {
        viewModel.fetchSportsData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // --- Header Section ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(cardBgColor, CircleShape)
                    .border(1.dp, borderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = textColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SPORTS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFFFF6B00)
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ZONE",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = textColor
                        )
                    )
                }
                Text(
                    text = "Live events & sports channels",
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { viewModel.fetchSportsData() },
                modifier = Modifier
                    .size(40.dp)
                    .background(cardBgColor, CircleShape)
                    .border(1.dp, borderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFFFF6B00)
                )
            }
        }

        // --- Custom Modern M3 Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .background(cardBgColor, RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabTitles = listOf("Live Matches", "Sports Channels")
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFFFF6B00) else Color.Transparent)
                        .clickable { selectedTab = index }
                        .wrapContentSize(Alignment.Center)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) Color.White else subTextColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Content Section ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> LiveEventsTab(
                    state = sportsEventsState,
                    viewModel = viewModel,
                    onNavigateToPlayer = onNavigateToPlayer,
                    cardBgColor = cardBgColor,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    borderColor = borderColor
                )
                1 -> SportsChannelsTab(
                    state = sportsChannelsState,
                    viewModel = viewModel,
                    onNavigateToPlayer = onNavigateToPlayer,
                    cardBgColor = cardBgColor,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    borderColor = borderColor
                )
            }
        }
    }
}

@Composable
fun LiveEventsTab(
    state: UiState<List<LiveMatch>>,
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    cardBgColor: Color,
    textColor: Color,
    subTextColor: Color,
    borderColor: Color
) {
    val coroutineScope = rememberCoroutineScope()

    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF6B00))
            }
        }
        is UiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.fetchSportsData() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                ) {
                    Text("Retry", color = Color.White)
                }
            }
        }
        is UiState.Success -> {
            val matches = state.data
            if (matches.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SportsSoccer,
                        contentDescription = "No Matches",
                        tint = subTextColor,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Live or Upcoming Sports Events",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                    Text(
                        text = "Please check back later",
                        style = MaterialTheme.typography.bodySmall,
                        color = subTextColor
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(matches, key = { it.id }) { match ->
                        LiveMatchItemCard(
                            match = match,
                            onPlayServer = { server ->
                                val channel = IptvChannel(
                                    name = "${match.title} - ${server.name}",
                                    url = server.url,
                                    logo = match.bannerUrl ?: match.teamA.logo ?: "",
                                    group = "Sports Live"
                                )
                                viewModel.setActiveChannel(channel)
                                onNavigateToPlayer()
                            },
                            cardBgColor = cardBgColor,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            borderColor = borderColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveMatchItemCard(
    match: LiveMatch,
    onPlayServer: (StreamServer) -> Unit,
    cardBgColor: Color,
    textColor: Color,
    subTextColor: Color,
    borderColor: Color
) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasServers = !match.servers.isNullOrEmpty()
    val isLive = match.status.equals("live", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasServers) { isExpanded = !isExpanded }
            .border(
                width = if (isLive) 1.5.dp else 1.dp,
                color = if (isLive) Color(0xFFFF6B00) else borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Category, Tournament and Live Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (match.sportCategory.lowercase()) {
                            "football", "soccer" -> Icons.Default.SportsSoccer
                            "cricket" -> Icons.Default.SportsCricket
                            "kabaddi" -> Icons.Default.SportsKabaddi
                            else -> Icons.Default.Sports
                        },
                        contentDescription = match.sportCategory,
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = match.tournament ?: match.sportCategory,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF6B00)
                    )
                }

                // Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isLive) Color.Red.copy(alpha = 0.15f)
                            else Color.Gray.copy(alpha = 0.15f)
                        )
                        .border(
                            1.dp,
                            if (isLive) Color.Red else Color.Gray,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else match.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (isLive) Color.Red else subTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scoreboard vs Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team A
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamLogoImage(url = match.teamA.logo, name = match.teamA.name)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.teamA.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Scores & VS
                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLive && match.teamA.score != null && match.teamB.score != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = match.teamA.score,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = textColor
                            )
                            Text(
                                text = " - ",
                                style = MaterialTheme.typography.titleMedium,
                                color = subTextColor,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Text(
                                text = match.teamB.score,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = textColor
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF6B00).copy(alpha = 0.1f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = Color(0xFFFF6B00)
                            )
                        }
                    }

                    if (match.badgeText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = match.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (match.startTime != null && !isLive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = match.startTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = subTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Team B
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamLogoImage(url = match.teamB.logo, name = match.teamB.name)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.teamB.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Title/Details of event (e.g. Argentina vs Brazil)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = match.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = subTextColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Server Streaming Links
            if (hasServers) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = borderColor)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Streaming Servers",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand Servers",
                        tint = Color(0xFFFF6B00)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        match.servers?.forEach { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFF6B00).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFFF6B00).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .clickable { onPlayServer(server) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Play",
                                        tint = Color(0xFFFF6B00),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = server.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = textColor
                                    )
                                }
                                Text(
                                    text = "Watch Now",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFF6B00)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamLogoImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(Color.White, CircleShape)
            .border(1.dp, Color(0xFFE4E4E7), CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = name,
                tint = Color(0xFFFF6B00),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SportsChannelsTab(
    state: UiState<List<SportChannel>>,
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    cardBgColor: Color,
    textColor: Color,
    subTextColor: Color,
    borderColor: Color
) {
    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF6B00))
            }
        }
        is UiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.fetchSportsData() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                ) {
                    Text("Retry", color = Color.White)
                }
            }
        }
        is UiState.Success -> {
            val channels = state.data
            if (channels.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "No Channels",
                        tint = subTextColor,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Sports Channels Available",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels, key = { it.id }) { ch ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable {
                                    val targetChannel = IptvChannel(
                                        name = ch.name,
                                        url = ch.url,
                                        logo = ch.logo ?: "",
                                        group = ch.group ?: "Sports"
                                    )
                                    viewModel.setActiveChannel(targetChannel)
                                    onNavigateToPlayer()
                                }
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!ch.logo.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(ch.logo)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = ch.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = ch.name,
                                            tint = Color(0xFFFF6B00),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = ch.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = textColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
