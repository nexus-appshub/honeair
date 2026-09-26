package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.IptvChannel
import com.example.data.model.LiveMatch
import com.example.data.model.SportChannel
import com.example.data.model.StreamServer
import com.example.ui.viewmodel.StreamViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch

data class SportCategoryItem(
    val id: String,
    val name: String,
    val emoji: String,
    val count: Int = 0
)

fun getSportCanonicalId(sport: String): String {
    val lower = sport.lowercase().trim()
    return when {
        lower.contains("cricket") -> "cricket"
        lower.contains("football") || lower.contains("soccer") -> "football"
        lower.contains("racing") || lower.contains("motor") || lower.contains("f1") || lower.contains("formula") || lower.contains("moto") -> "racing"
        lower.contains("combat") || lower.contains("wrestling") || lower.contains("wwe") || lower.contains("boxing") || lower.contains("ufc") || lower.contains("mma") -> "combat"
        lower.contains("basketball") || lower.contains("nba") -> "basketball"
        lower.contains("tennis") -> "tennis"
        lower.contains("badminton") -> "badminton"
        lower.contains("baseball") || lower.contains("mlb") -> "baseball"
        lower.contains("hockey") || lower.contains("nhl") -> "hockey"
        lower.contains("volleyball") -> "volleyball"
        lower.contains("rugby") || lower.contains("nfl") || lower.contains("american football") -> "rugby"
        lower.contains("kabaddi") -> "kabaddi"
        lower.contains("table tennis") || lower.contains("ping pong") -> "table_tennis"
        lower.contains("golf") -> "golf"
        lower.isNotBlank() -> lower.replace(" ", "_")
        else -> "other"
    }
}

fun getSportEmojiAndDisplayName(canonicalId: String, rawSport: String): Pair<String, String> {
    return when (canonicalId) {
        "cricket" -> "🏏" to "Cricket"
        "football" -> "⚽" to "Football"
        "racing" -> "🏎️" to "MotorSports"
        "combat" -> "🥊" to "Wrestling"
        "basketball" -> "🏀" to "Basketball"
        "tennis" -> "🎾" to "Tennis"
        "badminton" -> "🏸" to "Badminton"
        "baseball" -> "⚾" to "Baseball"
        "hockey" -> "🏑" to "Hockey"
        "volleyball" -> "🏐" to "Volleyball"
        "rugby" -> "🏉" to "Rugby"
        "kabaddi" -> "🤼" to "Kabaddi"
        "table_tennis" -> "🏓" to "Table Tennis"
        "golf" -> "⛳" to "Golf"
        else -> "🏅" to (rawSport.trim().ifBlank { "Other" }.replaceFirstChar { it.uppercase() })
    }
}

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
    val appControlConfig by viewModel.appControlConfig.collectAsState()
    val isSportsUnlockedLocally by viewModel.isSportsUnlockedLocally.collectAsState()

    val isDark = isSystemInDarkTheme()
    var showUnlockDialog by remember { mutableStateOf(false) }
    var enteredPasscode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf(false) }

    val bgColor = if (isDark) Color(0xFF0B1322) else Color(0xFFF4F4F5)
    val cardBgColor = if (isDark) Color(0xFF131D2D) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF18181B)
    val subTextColor = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val borderColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE4E4E7)

    val isLocked = (appControlConfig?.isSportsTabLocked == true) && !isSportsUnlockedLocally

    LaunchedEffect(Unit) {
        viewModel.fetchSportsData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // --- Locked Sports Overlay vs Active Content ---
        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.94f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF16101E) else Color.White),
                    border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFF3B30).copy(alpha = 0.6f), Color(0xFFFF6B00).copy(alpha = 0.3f))))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFFF3B30).copy(alpha = 0.2f), Color(0xFFFF6B00).copy(alpha = 0.1f))),
                                    CircleShape
                                )
                                .border(1.2.dp, Color(0xFFFF3B30), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF3B30).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "RESTRICTED ACCESS",
                                color = Color(0xFFFF453A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Sports & Live Hub Locked",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = appControlConfig?.sportsLockReason?.ifBlank { "Live cricket, football and premium streams are locked by admin." }
                                ?: "Live cricket, football and premium streams are locked by admin.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = subTextColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { showUnlockDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = "Passcode", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enter Access Passcode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            // --- Content Section ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                LiveEventsTab(
                    state = sportsEventsState,
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

    // Passcode Unlock Dialog
    if (showUnlockDialog) {
        Dialog(onDismissRequest = { showUnlockDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(0.96f).padding(8.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151422)),
                border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFF6B00).copy(alpha = 0.6f), Color(0xFF8B5CF6).copy(alpha = 0.4f))))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFFF6B00).copy(alpha = 0.15f), CircleShape)
                            .border(1.2.dp, Color(0xFFFF6B00).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Unlock Sports Hub",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Enter FanCode pass or admin passcode to unlock",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = enteredPasscode,
                        onValueChange = {
                            enteredPasscode = it
                            passcodeError = false
                        },
                        label = { Text("Passcode / FanCode") },
                        placeholder = { Text("Enter code...", color = Color.DarkGray) },
                        isError = passcodeError,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6B00),
                            unfocusedBorderColor = Color(0xFF2C2C3A),
                            focusedLabelColor = Color(0xFFFF6B00),
                            unfocusedLabelColor = Color(0xFFA1A1AA),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1B1B2A),
                            unfocusedContainerColor = Color(0xFF1B1B2A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (passcodeError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Invalid Passcode! Please verify the code.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF4D4D)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showUnlockDialog = false },
                            border = BorderStroke(1.dp, Color(0xFF383848)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Cancel", color = Color.White, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val success = viewModel.unlockSportsTabWithCode(enteredPasscode)
                                if (success) {
                                    showUnlockDialog = false
                                } else {
                                    passcodeError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
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
    val appControlConfig by viewModel.appControlConfig.collectAsState()

    var selectedCategory by remember { mutableStateOf("all") }
    var selectedStatus by remember { mutableStateOf("all") }

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
            val allMatches = state.data

            // Dynamic categories - Only show categories that have available events (count > 0)
            val availableCategories = remember(allMatches) {
                val result = mutableListOf<SportCategoryItem>()
                if (allMatches.isNotEmpty()) {
                    result.add(SportCategoryItem(id = "all", name = "All", emoji = "🏆", count = allMatches.size))
                }

                val grouped = allMatches.groupBy { getSportCanonicalId(it.sportCategory) }
                val standardOrder = listOf(
                    "cricket", "football", "racing", "combat", "basketball",
                    "tennis", "badminton", "baseball", "hockey", "volleyball",
                    "rugby", "kabaddi", "table_tennis", "golf"
                )

                for (sportId in standardOrder) {
                    val matches = grouped[sportId]
                    if (!matches.isNullOrEmpty()) {
                        val (emoji, name) = getSportEmojiAndDisplayName(sportId, matches.first().sportCategory)
                        result.add(SportCategoryItem(id = sportId, name = name, emoji = emoji, count = matches.size))
                    }
                }

                for ((sportId, matches) in grouped) {
                    if (sportId !in standardOrder && matches.isNotEmpty()) {
                        val (emoji, name) = getSportEmojiAndDisplayName(sportId, matches.first().sportCategory)
                        result.add(SportCategoryItem(id = sportId, name = name, emoji = emoji, count = matches.size))
                    }
                }

                result
            }

            // Auto-reset category to "all" if selected category is no longer present
            LaunchedEffect(availableCategories) {
                if (availableCategories.isNotEmpty() && availableCategories.none { it.id == selectedCategory }) {
                    selectedCategory = "all"
                }
            }

            // Filter logic for category and status
            val filteredMatches = remember(allMatches, selectedCategory, selectedStatus) {
                allMatches.filter { match ->
                    val matchesCat = if (selectedCategory == "all") true else {
                        val canonicalId = getSportCanonicalId(match.sportCategory)
                        canonicalId == selectedCategory || match.sportCategory.contains(selectedCategory, ignoreCase = true)
                    }
                    val matchesStatus = if (selectedStatus == "all") true else {
                        match.status.equals(selectedStatus, ignoreCase = true)
                    }
                    matchesCat && matchesStatus
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // 1. CIRCULAR CATEGORY SELECTOR WITH RED BADGE COUNTERS (Only categories with active events)
                if (availableCategories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        items(availableCategories, key = { it.id }) { cat ->
                            val isSelected = selectedCategory == cat.id
                            val count = cat.count

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { selectedCategory = cat.id }
                            ) {
                                Box(modifier = Modifier.size(54.dp)) {
                                    // Category Circle Ring
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color(0xFF131D2D))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = cat.emoji, fontSize = 22.sp)
                                    }

                                    // Top-Right Red Counter Badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cat.name,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 2. STATUS FILTER PILLS (All, Live, Upcoming, Recent)
                val statusList = listOf(
                    "all" to "All (${allMatches.size})",
                    "live" to "Live (${allMatches.count { it.status.equals("live", ignoreCase = true) }})",
                    "upcoming" to "Upcoming (${allMatches.count { it.status.equals("upcoming", ignoreCase = true) }})",
                    "ended" to "Recent (${allMatches.count { it.status.equals("ended", ignoreCase = true) || it.status.equals("recent", ignoreCase = true) }})"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(statusList) { (statusId, label) ->
                        val isSelected = selectedStatus == statusId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF132238) else Color(0xFF131D2D),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B)
                            ),
                            modifier = Modifier.clickable { selectedStatus = statusId }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. MATCH EVENTS LIST
                if (filteredMatches.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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
                            text = "No Matches Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = "Try changing category or status filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredMatches, key = { it.id }) { match ->
                            SportzfyEventCard(
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
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- SPORTZFY COMPACT EVENT CARD ---
@Composable
fun SportzfyEventCard(
    match: LiveMatch,
    onPlayServer: (StreamServer) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)
    val isLive = match.status.equals("live", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D)),
        border = BorderStroke(1.dp, if (isLive) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val servers = match.servers
                if (servers.isNullOrEmpty() || servers.size <= 1) {
                    val server = servers?.firstOrNull() ?: StreamServer(id = "srv-1", name = "Server 1", url = "")
                    onPlayServer(server)
                } else {
                    isExpanded = !isExpanded
                }
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 1. Header Row: Category || Tournament/Title (Left) & Time/Date (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val (symbol, tintColor) = when (match.sportCategory.lowercase()) {
                        "cricket" -> "🏏" to Color(0xFF00E5FF)
                        "football", "soccer" -> "⚡" to Color(0xFFFF4D4D)
                        "racing", "motorsports" -> "🏎️" to Color(0xFFFF9500)
                        "combat", "wrestling" -> "🥊" to Color(0xFFFF2D55)
                        else -> "🏆" to Color(0xFF00E5FF)
                    }
                    Text(text = symbol, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${match.sportCategory.replaceFirstChar { it.uppercase() }} || ${match.tournament ?: match.title}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Cyan / Bright Time & Date on Right
                if (isLive) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFDC2626)
                    ) {
                        Text(
                            text = "🔴 LIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = match.startTime ?: "Live Now",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E293B),
                thickness = 0.6.dp,
                modifier = Modifier.padding(vertical = 10.dp)
            )

            // 2. Main Teams Scoreboard Row: [Logo A + Name A & Score A]  VS (Game State)  [Name B & Score B + Logo B]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team A (Left)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF2E3D56), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!match.teamA.logo.isNullOrBlank()) {
                            AsyncImage(
                                model = match.teamA.logo,
                                contentDescription = match.teamA.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = match.teamA.name,
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = match.teamA.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!match.teamA.score.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = match.teamA.score,
                                color = Color(0xFFFF6B00),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // VS Center & Game State
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = "VS",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    val gameDetail = match.gameState?.overs?.let { "$it ov" }
                        ?: match.gameState?.minute?.let { "${it}'" }
                        ?: match.gameState?.period
                        ?: match.gameState?.statusText
                    if (!gameDetail.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = gameDetail,
                                color = Color(0xFF00E5FF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Team B (Right)
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = match.teamB.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        if (!match.teamB.score.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = match.teamB.score,
                                color = Color(0xFFFF6B00),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF2E3D56), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!match.teamB.logo.isNullOrBlank()) {
                            AsyncImage(
                                model = match.teamB.logo,
                                contentDescription = match.teamB.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = match.teamB.name,
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 3. Streaming Servers Dropdown (When Available)
            val servers = match.servers ?: emptyList()
            if (servers.size > 1) {
                HorizontalDivider(
                    color = Color(0xFF1E293B),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(top = 10.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Streaming Servers",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x3300E5FF)
                        ) {
                            Text(
                                text = "${servers.size} ONLINE",
                                color = Color(0xFF00E5FF),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        servers.forEachIndexed { index, server ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0B1322),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayServer(server) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0x3300E5FF)
                                        ) {
                                            Text(
                                                text = "S${index + 1}",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = server.name,
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF1E293B)
                                    ) {
                                        Text(
                                            text = server.quality ?: "FHD",
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                        group = ch.group ?: "Sports",
                                        isPremium = ch.isPremium
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
