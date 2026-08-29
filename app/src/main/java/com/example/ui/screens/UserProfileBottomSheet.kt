package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

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
    onDeleteAccount: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF141416) else Color(0xFFF9F9FA)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) Color(0xFFA1A1A6) else Color(0xFF636366)
    val borderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        scrimColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                Spacer(modifier = Modifier.height(24.dp))
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
