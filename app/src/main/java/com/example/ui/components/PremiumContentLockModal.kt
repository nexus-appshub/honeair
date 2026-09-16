package com.example.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ad.StartIoAdManager
import com.example.data.model.IptvChannel
import com.example.data.model.MediaItem
import com.example.subscription.TemporaryUnlockManager
import com.example.ui.viewmodel.StreamViewModel

data class ContentLockState(
    val itemId: String,
    val title: String,
    val isChannel: Boolean = false,
    val channel: IptvChannel? = null,
    val mediaItem: MediaItem? = null,
    val season: Int = 1,
    val episode: Int = 1
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumContentLockModal(
    state: ContentLockState,
    viewModel: StreamViewModel,
    onDismiss: () -> Unit,
    onOpenVipSubscription: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isAdLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF120B22),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Gray.copy(alpha = 0.5f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Lock Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF007A).copy(alpha = 0.35f), Color.Transparent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Content Locked",
                    tint = Color(0xFFFF007A),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Premium Content Locked",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "’${state.title}’ is a VIP exclusive title. Upgrade to VIP or watch a short video ad to unlock it for 30 minutes.",
                fontSize = 13.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Option 2: Watch Rewarded Video Ad (30 Mins Pass)
            Button(
                onClick = {
                    if (activity == null) {
                        Toast.makeText(context, "Activity unavailable", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isAdLoading = true
                    StartIoAdManager.showRewardedVideo(
                        activity = activity,
                        onRewardEarned = {
                            isAdLoading = false
                            TemporaryUnlockManager.grantTemporaryUnlock(state.itemId, context, 30)
                            Toast.makeText(context, "🎉 '${state.title}' unlocked for 30 minutes!", Toast.LENGTH_LONG).show()
                            onDismiss()

                            // Immediately start playback
                            if (state.isChannel && state.channel != null) {
                                viewModel.setActiveChannel(state.channel)
                            } else if (state.mediaItem != null) {
                                viewModel.playMediaItem(state.mediaItem, state.season, state.episode)
                            }
                        },
                        onAdFailed = {
                            isAdLoading = false
                            Toast.makeText(context, "Video ad not ready or closed early. Could not unlock.", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                enabled = !isAdLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF007A),
                    contentColor = Color.White
                )
            ) {
                if (isAdLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Watch Ad (Unlock for 30 Mins)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 1: Upgrade to VIP (Unlimited Ad-Free)
            OutlinedButton(
                onClick = onOpenVipSubscription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFFFD700)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFFD700)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Upgrade to VIP (Unlimited Ad-Free)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
