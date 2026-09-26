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

// Formats timestamp or ISO date string into "Sep-26, 12:44 PM"
fun formatEventDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "Live Now"
    val trimmed = raw.trim()

    // 1. Numeric timestamp (seconds or milliseconds)
    val numericVal = trimmed.toLongOrNull()
    if (numericVal != null) {
        val millis = if (numericVal < 100_000_000_000L) numericVal * 1000L else numericVal
        val date = java.util.Date(millis)
        val sdf = java.text.SimpleDateFormat("MMM-dd, hh:mm a", java.util.Locale.ENGLISH)
        sdf.timeZone = java.util.TimeZone.getDefault()
        return sdf.format(date)
    }

    // 2. Parse standard date patterns
    val inputPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "dd-MM-yyyy HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss",
        "MMM dd, yyyy HH:mm",
        "MMM dd, yyyy hh:mm a",
        "MMM dd, yyyy",
        "MMMM dd, yyyy hh:mm a"
    )

    for (pattern in inputPatterns) {
        try {
            val parser = java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH)
            if (pattern.endsWith("X") || pattern.endsWith("Z") || pattern.contains("'T'")) {
                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(trimmed)
            if (parsedDate != null) {
                val outFormat = java.text.SimpleDateFormat("MMM-dd, hh:mm a", java.util.Locale.ENGLISH)
                outFormat.timeZone = java.util.TimeZone.getDefault()
                return outFormat.format(parsedDate)
            }
        } catch (_: Exception) {}
    }

    // 3. Regex match for ISO-like dates (e.g. 2026-09-26 12:44)
    try {
        val isoDateRegex = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})[T ](\\d{1,2}):(\\d{1,2})")
        val match = isoDateRegex.find(trimmed)
        if (match != null) {
            val (year, month, day, hour, min) = match.destructured
            val cal = java.util.Calendar.getInstance()
            cal.set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), min.toInt(), 0)
            val outFormat = java.text.SimpleDateFormat("MMM-dd, hh:mm a", java.util.Locale.ENGLISH)
            return outFormat.format(cal.time)
        }
    } catch (_: Exception) {}

    return trimmed
}

// Extracts duration number for live match without extra text
fun getLiveMatchDuration(match: LiveMatch): String? {
    // 1. Minute from gameState (e.g. "64", "45+2")
    val rawMinute = match.gameState?.minute?.trim()
    if (!rawMinute.isNullOrBlank()) {
        val clean = rawMinute.replace("'", "").replace("min", "").replace("m", "").trim()
        return if (clean.all { it.isDigit() || it == '+' }) "$clean'" else clean
    }

    // 2. Overs from gameState (Cricket)
    val overs = match.gameState?.overs?.trim()
    if (!overs.isNullOrBlank()) {
        return "$overs ov"
    }

    // 3. Period or short status text (HT, 1H, 2H, Q1, Q2)
    val period = match.gameState?.period?.trim() ?: match.gameState?.statusText?.trim()
    if (!period.isNullOrBlank() && period.length <= 8) {
        return period
    }

    // 4. Elapsed minutes calculated from startTime if live
    val startTimeStr = match.startTime?.trim()
    if (!startTimeStr.isNullOrBlank()) {
        var startMillis: Long? = startTimeStr.toLongOrNull()
        if (startMillis != null && startMillis < 100_000_000_000L) {
            startMillis *= 1000L
        }
        if (startMillis == null) {
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm"
            )
            for (p in patterns) {
                try {
                    val sdf = java.text.SimpleDateFormat(p, java.util.Locale.ENGLISH)
                    if (p.contains("'T'") || p.endsWith("X")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val d = sdf.parse(startTimeStr)
                    if (d != null) {
                        startMillis = d.time
                        break
                    }
                } catch (_: Exception) {}
            }
        }
        if (startMillis != null) {
            val elapsedMs = System.currentTimeMillis() - startMillis
            if (elapsedMs in 0..(150 * 60 * 1000L)) {
                val elapsedMinutes = elapsedMs / (60 * 1000L)
                return "$elapsedMinutes'"
            }
        }
    }

    // 5. Fallback badge text if it looks like a duration
    val badge = match.badgeText?.trim()
    if (!badge.isNullOrBlank() && (badge.contains("'") || badge.contains("min") || badge.matches(Regex("\\d+")))) {
        val clean = badge.replace("'", "").replace("min", "").trim()
        return if (clean.all { it.isDigit() }) "$clean'" else badge
    }

    return null
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
                                    // Category Circle Ring with Orange Theme
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF261508) else Color(0xFF131D2D))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                brush = if (isSelected) Brush.linearGradient(listOf(Color(0xFFFF6B00), Color(0xFFFF9E00))) else Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B))),
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
                                    color = if (isSelected) Color(0xFFFF8800) else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 2. STATUS FILTER PILLS (All, Live, Upcoming, Recent) with Orange Gradient
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
                            color = if (isSelected) Color(0xFF261508) else Color(0xFF131D2D),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                brush = if (isSelected) Brush.linearGradient(listOf(Color(0xFFFF6B00), Color(0xFFFF9E00))) else Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
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
                                    color = if (isSelected) Color(0xFFFF8800) else Color.Gray,
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
        border = BorderStroke(1.dp, if (isLive) Color(0xFFFF6B00).copy(alpha = 0.6f) else Color(0xFF1E293B)),
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
            // 1. Header Row: Category || Tournament/Title (Left) & Time/Date/Live Duration (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val (symbol, _) = when (match.sportCategory.lowercase()) {
                        "cricket" -> "🏏" to Color(0xFFFF6B00)
                        "football", "soccer" -> "⚡" to Color(0xFFFF4D4D)
                        "racing", "motorsports" -> "🏎️" to Color(0xFFFF9500)
                        "combat", "wrestling" -> "🥊" to Color(0xFFFF2D55)
                        else -> "🏆" to Color(0xFFFF8800)
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

                // Right Side: Live Badge + Small Live Duration Number (e.g. 64') OR Formatted Date-Time (Sep-26, 12:44 PM)
                if (isLive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
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

                        val duration = getLiveMatchDuration(match)
                        if (!duration.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(0.6.dp, Color(0xFFFF6B00).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = duration,
                                    color = Color(0xFFFF9E00),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = formatEventDateTime(match.startTime),
                        color = Color(0xFFFF9E00),
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
                                color = Color(0xFFFF7A00),
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
                        ?: match.gameState?.minute?.let { "${it.replace("'", "")}'" }
                        ?: match.gameState?.period
                        ?: match.gameState?.statusText
                    if (!gameDetail.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF6B00).copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, Color(0xFFFF6B00).copy(alpha = 0.4f)),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = gameDetail,
                                color = Color(0xFFFF9E00),
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
                                color = Color(0xFFFF7A00),
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
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Streaming Servers",
                            color = Color(0xFFFF8800),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x33FF6B00)
                        ) {
                            Text(
                                text = "${servers.size} ONLINE",
                                color = Color(0xFFFF9E00),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFFFF8800),
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
                                            color = Color(0x33FF6B00)
                                        ) {
                                            Text(
                                                text = "S${index + 1}",
                                                color = Color(0xFFFF9E00),
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
