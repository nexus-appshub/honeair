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
    val emoji: String
)

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
        // --- Remote Control Header & Circular Status Pill ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SportsBasketball,
                    contentDescription = "Sports",
                    tint = Color(0xFFFF6B00),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sports Hub",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = textColor
                )
            }

            // Remote controllable Circular Status Button
            val statusText = appControlConfig?.sportsTabStatusText ?: "Live"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isLocked) Color(0xFFFF3B30).copy(alpha = 0.15f) else Color(0xFFFF6B00).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isLocked) Color(0xFFFF3B30) else Color(0xFFFF6B00)),
                    modifier = Modifier
                        .clickable(enabled = isLocked) { showUnlockDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isLocked) Color(0xFFFF3B30) else Color(0xFF34C759), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLocked) "LOCKED" else statusText.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isLocked) Color(0xFFFF3B30) else Color(0xFFFF6B00)
                        )
                        if (isLocked) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Custom Modern M3 Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
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

    val categories = remember {
        listOf(
            SportCategoryItem("all", "All", "🏆"),
            SportCategoryItem("cricket", "Cricket", "🏏"),
            SportCategoryItem("football", "Football", "⚽"),
            SportCategoryItem("racing", "MotorSports", "🏎️"),
            SportCategoryItem("combat", "Wrestling", "🥊")
        )
    }

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

            // Filter logic for category and status
            val filteredMatches = remember(allMatches, selectedCategory, selectedStatus) {
                allMatches.filter { match ->
                    val matchesCat = if (selectedCategory == "all") true else {
                        when (selectedCategory) {
                            "cricket" -> match.sportCategory.contains("cricket", ignoreCase = true)
                            "football" -> match.sportCategory.contains("football", ignoreCase = true) || match.sportCategory.contains("soccer", ignoreCase = true)
                            "racing" -> match.sportCategory.contains("racing", ignoreCase = true) || match.sportCategory.contains("motor", ignoreCase = true) || match.sportCategory.contains("f1", ignoreCase = true)
                            "combat" -> match.sportCategory.contains("combat", ignoreCase = true) || match.sportCategory.contains("wrestling", ignoreCase = true) || match.sportCategory.contains("boxing", ignoreCase = true) || match.sportCategory.contains("ufc", ignoreCase = true)
                            else -> match.sportCategory.contains(selectedCategory, ignoreCase = true)
                        }
                    }
                    val matchesStatus = if (selectedStatus == "all") true else {
                        match.status.equals(selectedStatus, ignoreCase = true)
                    }
                    matchesCat && matchesStatus
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // 2. CIRCULAR CATEGORY SELECTOR WITH RED BADGE COUNTERS
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat.id
                        val count = if (cat.id == "all") {
                            allMatches.size
                        } else {
                            allMatches.count { match ->
                                when (cat.id) {
                                    "cricket" -> match.sportCategory.contains("cricket", ignoreCase = true)
                                    "football" -> match.sportCategory.contains("football", ignoreCase = true) || match.sportCategory.contains("soccer", ignoreCase = true)
                                    "racing" -> match.sportCategory.contains("racing", ignoreCase = true) || match.sportCategory.contains("motor", ignoreCase = true) || match.sportCategory.contains("f1", ignoreCase = true)
                                    "combat" -> match.sportCategory.contains("combat", ignoreCase = true) || match.sportCategory.contains("wrestling", ignoreCase = true) || match.sportCategory.contains("boxing", ignoreCase = true)
                                    else -> match.sportCategory.contains(cat.id, ignoreCase = true)
                                }
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedCategory = cat.id }
                        ) {
                            Box(modifier = Modifier.size(56.dp)) {
                                // Category Circle Ring
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color(0xFF131D2D))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFFF6B00) else Color(0xFF1E293B),
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
                                color = if (isSelected) Color(0xFFFF6B00) else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 3. STATUS FILTER PILLS (All, Live, Upcoming, Recent)
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
                            color = Color(0xFF131D2D),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFFF6B00) else Color(0xFF1E293B)
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
                                        tint = Color(0xFFFF6B00),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFFFF6B00) else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 4. MATCH EVENTS LIST
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

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Category || Tournament & Time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val emoji = when (match.sportCategory.lowercase()) {
                        "cricket" -> "🏏"
                        "football", "soccer" -> "⚽"
                        "combat", "wrestling" -> "🥊"
                        else -> "🏎️"
                    }
                    Text(text = emoji, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${match.sportCategory.replaceFirstChar { it.uppercase() }} || ${match.tournament ?: match.title}",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = match.startTime ?: "11:00 pm",
                    color = Color(0xFFFF6B00), // Orange Time Text
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

            // Main Teams Scoreboard Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team A
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
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
                    Text(
                        text = match.teamA.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // VS / Score Center
                Text(
                    text = if (!match.teamA.score.isNullOrBlank()) "${match.teamA.score} - ${match.teamB.score ?: ""}" else "VS",
                    color = Color(0xFFFF6B00),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Team B
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = match.teamB.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
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

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

            // Collapsible Streaming Servers Accordion Header
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
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Streaming Servers",
                        color = Color(0xFFFF6B00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x33FF6B00)
                    ) {
                        Text(
                            text = "${match.servers?.size ?: 1} ONLINE",
                            color = Color(0xFFFF6B00),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFFF6B00),
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            // Expanded Server Buttons
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val servers = match.servers ?: listOf(StreamServer("Main FHD Stream", "", "FHD"))
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
                                        color = Color(0x33FF6B00)
                                    ) {
                                        Text(
                                            text = "S${index + 1}",
                                            color = Color(0xFFFF6B00),
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
