package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.IptvChannel
import com.example.data.model.SportsEvent
import com.example.ui.theme.*
import com.example.ui.viewmodel.StreamViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsSection(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("Matches") } // "Matches" or "Channels"
    var channelSearchQuery by remember { mutableStateOf("") }
    var selectedEventForServerSelection by remember { mutableStateOf<SportsEvent?>(null) }
    
    val sportsEvents by viewModel.sportsEventsState.collectAsState()
    val sportsChannelsState by viewModel.sportsChannelsState.collectAsState()
    
    val isDark = isSystemInDarkTheme()
    val bgGradient = if (isDark) {
        Brush.verticalGradient(colors = listOf(DeepSlate, Color.Black))
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFFF8F9FA), Color(0xFFE9ECEF)))
    }

    // Trigger data fetch on launch
    LaunchedEffect(Unit) {
        viewModel.fetchSportsChannels()
        viewModel.startSportsEventsListener()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Neon-Styled Title Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(NeonCyan.copy(alpha = 0.15f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(NeonCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsCricket,
                            contentDescription = "Sports",
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "LIVE SPORTS HUB",
                            color = if (isDark) TextPrimary else Color.Black,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Watch 24/7 channels & live match updates",
                            color = if (isDark) TextSecondary else Color.Gray,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-Navigation Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Matches" to "Live Matches", "Channels" to "24/7 Channels").forEach { (tabKey, tabTitle) ->
                    val isSelected = selectedTab == tabKey
                    val buttonColor = if (isSelected) NeonCyan else if (isDark) Color(0xFF1E1E24) else Color(0xFFE9ECEF)
                    val textColor = if (isSelected) Color.Black else if (isDark) TextPrimary else Color.DarkGray
                    
                    Surface(
                        onClick = { selectedTab = tabKey },
                        shape = RoundedCornerShape(20.dp),
                        color = buttonColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) NeonCyan else if (isDark) Color(0xFF2C2C35) else Color(0xFFCED4DA)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = tabTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                        }
                    }
                }
            }

            // Main Contents
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == "Matches") {
                    SportsMatchesList(
                        events = sportsEvents,
                        onEventClick = { selectedEventForServerSelection = it }
                    )
                } else {
                    SportsChannelsView(
                        state = sportsChannelsState,
                        searchQuery = channelSearchQuery,
                        onSearchQueryChange = { channelSearchQuery = it },
                        onChannelClick = { channel ->
                            // Custom headers for website-originated HLS streams
                            val headers = mapOf(
                                "Referer" to "https://www.hmair.xyz/",
                                "Origin" to "https://www.hmair.xyz",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            )
                            viewModel.setActiveChannel(channel, headers)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }

        // Server Selection Bottom Sheet
        if (selectedEventForServerSelection != null) {
            val event = selectedEventForServerSelection!!
            ModalBottomSheet(
                onDismissRequest = { selectedEventForServerSelection = null },
                containerColor = if (isDark) DeepSlate else Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Server",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) TextPrimary else Color.Black
                    )
                    Text(
                        text = "${event.teamA.name} vs ${event.teamB.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) NeonCyan else Color.DarkGray,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    if (event.servers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No stream servers available for this event yet.",
                                color = if (isDark) TextSecondary else Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(event.servers) { server ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF16161A) else Color(0xFFF1F3F5)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isDark) Color(0xFF2A2A32) else Color(0xFFE2E8F0)
                                    ),
                                    onClick = {
                                        val headersMap = mutableMapOf<String, String>()
                                        headersMap["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                        server.referer?.let { headersMap["Referer"] = it }
                                        server.origin?.let { headersMap["Origin"] = it }
                                        
                                        val channelName = "${event.tournament}: ${event.teamA.name} vs ${event.teamB.name}"
                                        viewModel.setActiveChannel(
                                            IptvChannel(name = channelName, url = server.url),
                                            headersMap
                                        )
                                        selectedEventForServerSelection = null
                                        onNavigateToPlayer()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = "Play",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = server.name,
                                                color = if (isDark) TextPrimary else Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Select",
                                            tint = if (isDark) TextSecondary else Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SportsMatchesList(
    events: List<SportsEvent>,
    onEventClick: (SportsEvent) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    if (events.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1E24) else Color(0xFFF1F3F5)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C35) else Color(0xFFCED4DA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsCricket,
                        contentDescription = "No events",
                        tint = if (isDark) TextSecondary else Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Live Matches currently active.",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) TextPrimary else Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Check back later for active tournaments and events.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) TextSecondary else Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(events, key = { it.id }) { event ->
            SportsEventCard(event = event, onClick = { onEventClick(event) })
        }
    }
}

@Composable
fun SportsEventCard(
    event: SportsEvent,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF131317) else Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (event.isLive) Color(0xFFFF6A00).copy(alpha = 0.8f) else if (isDark) Color(0xFF23232C) else Color(0xFFE2E8F0)
        ),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 6f
                shape = RoundedCornerShape(16.dp)
                clip = true
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Category, Tournament & Live Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (event.isLive) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer { alpha = livePulseAlpha }
                                .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(BorderStroke(1.dp, Color.Red), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "● LIVE NOW",
                                color = Color.Red,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    if (event.sportCategory.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF6A00).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = event.sportCategory.uppercase(),
                                color = Color(0xFFFF8A00),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (event.tournament.isNotEmpty()) {
                        Text(
                            text = event.tournament.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isDark) TextSecondary else Color.DarkGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!event.isLive && event.status.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(if (isDark) Color(0xFF22222A) else Color(0xFFF1F3F5), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.status.uppercase(),
                            color = if (isDark) TextSecondary else Color.DarkGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title if present
            if (event.title.isNotEmpty()) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = if (isDark) TextPrimary else Color.Black
                )
                if (event.description.isNotEmpty()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextSecondary else Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Teams VS Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Team A
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF8F9FA),
                                shape = CircleShape
                            )
                            .border(1.dp, if (isDark) Color(0xFF2C2C35) else Color(0xFFE2E8F0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (event.teamA.logo.isNotEmpty()) {
                            AsyncImage(
                                model = event.teamA.logo,
                                contentDescription = event.teamA.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsCricket,
                                contentDescription = "Team A",
                                tint = if (isDark) TextSecondary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.teamA.name.ifEmpty { "Team 1" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) TextPrimary else Color.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.teamA.score.isNotEmpty()) {
                        Text(
                            text = event.teamA.score,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                            color = Color(0xFFFF8A00),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Center VS Box
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    if (event.teamA.score.isNotEmpty() && event.teamB.score.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF6A00).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${event.teamA.score} - ${event.teamB.score}",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color(0xFFFF8A00)
                            )
                        }
                    } else {
                        Text(
                            text = "VS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (event.isLive) Color(0xFFFF8A00) else if (isDark) TextSecondary else Color.LightGray
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.displayTime,
                        fontSize = 10.sp,
                        color = if (isDark) TextSecondary.copy(alpha = 0.7f) else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Team B
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF8F9FA),
                                shape = CircleShape
                            )
                            .border(1.dp, if (isDark) Color(0xFF2C2C35) else Color(0xFFE2E8F0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (event.teamB.logo.isNotEmpty()) {
                            AsyncImage(
                                model = event.teamB.logo,
                                contentDescription = event.teamB.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsCricket,
                                contentDescription = "Team B",
                                tint = if (isDark) TextSecondary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.teamB.name.ifEmpty { "Team 2" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) TextPrimary else Color.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.teamB.score.isNotEmpty()) {
                        Text(
                            text = event.teamB.score,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                            color = Color(0xFFFF8A00),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Footer: Server list summary & CTA
            Divider(color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF1F3F5), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${event.servers.size} Stream Server(s)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) TextSecondary else Color.Gray
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Watch Live Match",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8A00)
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFFFF8A00),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SportsChannelsView(
    state: UiState<List<IptvChannel>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (IptvChannel) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Modern Search bar
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Search sports channels...",
                    color = if (isDark) TextSecondary.copy(alpha = 0.6f) else Color.Gray
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isDark) TextSecondary else Color.Gray
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = if (isDark) TextSecondary else Color.Gray
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = if (isDark) Color(0xFF1E1E24) else Color(0xFFF1F3F5),
                unfocusedContainerColor = if (isDark) Color(0xFF14141A) else Color(0xFFE9ECEF),
                focusedIndicatorColor = NeonCyan,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = if (isDark) TextPrimary else Color.Black,
                unfocusedTextColor = if (isDark) TextPrimary else Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        when (state) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            is UiState.Success -> {
                val filtered = remember(state.data, searchQuery) {
                    if (searchQuery.isEmpty()) {
                        state.data
                    } else {
                        state.data.filter { it.name.lowercase().contains(searchQuery.lowercase()) }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No channels match your search.",
                            color = if (isDark) TextSecondary else Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered) { channel ->
                            SportsChannelCard(channel = channel, onClick = { onChannelClick(channel) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SportsChannelCard(
    channel: IptvChannel,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "channelScale"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF131317) else Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPressed) NeonCyan else if (isDark) Color(0xFF23232C) else Color(0xFFE2E8F0)
        ),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Channel Logo Box
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF8F9FA),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (isDark) Color(0xFF2A2A32) else Color(0xFFE2E8F0),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = channel.name,
                        tint = if (isDark) TextSecondary else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) TextPrimary else Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
