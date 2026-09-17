package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import com.example.subscription.SubscriptionManager
import com.example.ui.components.SubscriptionPlanModal
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.StreamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileBottomSheet(
    profile: com.example.ui.viewmodel.UserProfile?,
    selectedAudioIndex: Int,
    onDismiss: () -> Unit,
    onShowWatchHistory: () -> Unit,
    onShowWebVersion: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onShowCopyrightAlert: () -> Unit = {},
    onShowFloatingPlayerLimit: () -> Unit = {}
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF141416) else Color(0xFFF9F9FA)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) Color(0xFFA1A1A6) else Color(0xFF636366)
    val borderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    val isPremium by SubscriptionManager.isPremium.collectAsState()
    val isExpired by SubscriptionManager.isExpired.collectAsState()
    val planName by SubscriptionManager.subscriptionPlan.collectAsState()
    val expiryText by SubscriptionManager.expiryDate.collectAsState()
    val expiryTimestamp by SubscriptionManager.expiryTimestamp.collectAsState()
    val viewModel: StreamViewModel = viewModel()
    val isRedeemActive by viewModel.isRedeemActive.collectAsState()
    val redeemExpiry = if (isRedeemActive) viewModel.getRedeemUnlockExpiry() else 0L
    val redeemPlanName = if (isRedeemActive) viewModel.getRedeemPlanName() else ""
    val effectiveIsPremium = (viewModel.isUserPremium(profile?.email) || isRedeemActive) && !isExpired
    var showPlanModal by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        scrimColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "User Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (profile != null) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(com.example.ui.theme.NeonCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.NeonCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = profile.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = profile.email,
                    fontSize = 14.sp,
                    color = subTextColor
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Subscription Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        effectiveIsPremium -> Color(0xFF1B3828)
                        isExpired -> Color(0xFF351C1C)
                        else -> Color(0xFF241C35)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    when {
                        effectiveIsPremium -> Color(0xFF4CAF50)
                        isExpired -> Color(0xFFFF4444).copy(alpha = 0.6f)
                        else -> Color(0xFFFFD700).copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        when {
                                            effectiveIsPremium -> Color(0xFF4CAF50)
                                            isExpired -> Color(0xFFFF4444)
                                            else -> Color(0xFFFFD700)
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        effectiveIsPremium -> Icons.Filled.Verified
                                        isExpired -> Icons.Filled.Star
                                        else -> Icons.Filled.WorkspacePremium
                                    },
                                    contentDescription = null,
                                    tint = if (isExpired) Color.White else Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Subscription Status",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = when {
                                        isRedeemActive -> "VIP Active ($redeemPlanName)"
                                        effectiveIsPremium -> "VIP Premium Active"
                                        isExpired -> "Subscription Expired"
                                        else -> "Free Plan"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        effectiveIsPremium -> Color(0xFF81C784)
                                        isExpired -> Color(0xFFFF6B6B)
                                        else -> Color(0xFFFFD700)
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = { showPlanModal = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    effectiveIsPremium -> Color(0xFF2E7D32)
                                    isExpired -> Color(0xFFFF4444)
                                    else -> Color(0xFFFFD700)
                                },
                                contentColor = when {
                                    effectiveIsPremium -> Color.White
                                    isExpired -> Color.White
                                    else -> Color.Black
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = when {
                                    effectiveIsPremium -> "Manage"
                                    isExpired -> "Renew Now"
                                    else -> "Upgrade Now"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Remaining Subscription / Promo Duration Display
                    val remainingDuration = if (isRedeemActive) {
                        SubscriptionManager.getRemainingTimeDescription(redeemExpiry, null)
                    } else if (isPremium) {
                        SubscriptionManager.getRemainingTimeDescription(expiryTimestamp, expiryText)
                    } else null

                    if (effectiveIsPremium && remainingDuration != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Time Left: $remainingDuration",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC8E6C9)
                                    )
                                    val dateSubText = if (isRedeemActive) {
                                        val fmt = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                        "Plan: $redeemPlanName • Expires on: ${fmt.format(java.util.Date(redeemExpiry))}"
                                    } else {
                                        expiryText ?: "Active Subscription"
                                    }
                                    Text(
                                        text = dateSubText,
                                        fontSize = 10.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    if (isExpired) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your VIP subscription period has ended ($expiryText). Renew now to restore ad-free viewing and episode unlocks.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFCDD2)
                        )
                    } else if (!effectiveIsPremium) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Upgrade to unlock all series episodes & remove 100% of ads.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                    } else if (expiryText != null && remainingDuration == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Plan: $planName • $expiryText",
                            fontSize = 11.sp,
                            color = Color(0xFFA5D6A7)
                        )
                    }
                }
            }

            // Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                UserProfileMenuItem(
                    icon = Icons.Default.History,
                    text = "Watch History",
                    onClick = { onDismiss(); onShowWatchHistory() },
                    textColor = textColor
                )
                HorizontalDivider(color = borderColor)
                UserProfileMenuItem(
                    icon = Icons.Default.Language,
                    text = "Web Version",
                    onClick = { onDismiss(); onShowWebVersion() },
                    textColor = textColor
                )
                HorizontalDivider(color = borderColor)
                UserProfileMenuItem(
                    icon = Icons.Outlined.Shield,
                    text = "Copyright Alert",
                    onClick = { onDismiss(); onShowCopyrightAlert() },
                    textColor = Color(0xFFFF6B00)
                )
                HorizontalDivider(color = borderColor)
                UserProfileMenuItem(
                    icon = Icons.Default.PictureInPictureAlt,
                    text = "Multiple Floating Player Mode",
                    onClick = { onDismiss(); onShowFloatingPlayerLimit() },
                    textColor = Color(0xFF00E5FF)
                )
                HorizontalDivider(color = borderColor)
                UserProfileMenuItem(
                    icon = Icons.Default.SwitchAccount,
                    text = "Switch Account",
                    onClick = { onDismiss(); onSwitchAccount() },
                    textColor = textColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                UserProfileMenuItem(
                    icon = Icons.Default.ExitToApp,
                    text = "Sign Out",
                    onClick = { onDismiss(); onSignOut() },
                    textColor = com.example.ui.theme.NeonCyan
                )
                HorizontalDivider(color = borderColor)
                UserProfileMenuItem(
                    icon = Icons.Default.Delete,
                    text = "Delete Account",
                    onClick = { onDismiss(); onDeleteAccount() },
                    textColor = Color.Red
                )
            }
        }

        SubscriptionPlanModal(
            isVisible = showPlanModal,
            onDismiss = { showPlanModal = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyrightBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = "Copyright Disclaimer",
                    tint = Color(0xFFFF6B00),
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Copyright Disclaimer",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2C2C2E),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = "Copyright Disclaimer & Intellectual Property Notice",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    item {
                        Text(
                            text = "Home Air functions strictly as an indexing directory and media player utility. Home Air does not host, upload, broadcast, store, or retransmit any audio, video, or media content. All content streams, embedded links, and playlists indexed within this application are hosted by independent third-party servers and are publicly and freely accessible across the internet.",
                            color = Color(0xFFD1D1D6),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    item {
                        Text(
                            text = "All trademarks, registered service marks, logos, and copyrighted materials displayed or accessed through this application remain the exclusive property of their respective owners. Home Air claims no ownership, affiliation, or endorsement regarding any third-party content.",
                            color = Color(0xFFD1D1D6),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    item {
                        HorizontalDivider(color = Color(0xFF3A3A3C))
                    }

                    item {
                        Text(
                            text = "Notice and Takedown Procedure:",
                            color = Color(0xFFFF9E40),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    item {
                        Text(
                            text = "Home Air complies with applicable copyright regulations, including the Digital Millennium Copyright Act (DMCA). If you are a copyright holder or an authorized agent and believe that an indexed link infringes upon your intellectual property rights, please submit a written notice containing proof of ownership and the exact URL/content identifier to:",
                            color = Color(0xFFD1D1D6),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    item {
                        Surface(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:hmairtv@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "DMCA / Copyright Infringement Notice")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Email: hmairtv@gmail.com", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF222224),
                            border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text("Contact Legal Email", color = Color.Gray, fontSize = 11.sp)
                                    Text("hmairtv@gmail.com", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Upon receipt of a valid infringement notice, the referenced links will be investigated and removed expeditiously.",
                            color = Color(0xFFD1D1D6),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("I Understand", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun UserProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingPlayerLimitBottomSheet(
    currentLimit: Int,
    onSelectLimit: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "Floating Player Mode",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "Multiple Floating Player Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Select max concurrent active floating players (2 to 6 max)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val options = listOf(2, 3, 4, 5, 6)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEach { limit ->
                    val isSelected = currentLimit == limit
                    Surface(
                        onClick = {
                            onSelectLimit(limit)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF2C2C2E),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF3A3A3C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "$limit Floating Players ${if (limit == 6) "(Max)" else if (limit == 2) "(Standard)" else ""}",
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Allows playing up to $limit floating video players simultaneously",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectLimit(limit)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF00E5FF),
                                    unselectedColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
