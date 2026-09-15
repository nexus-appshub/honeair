package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.subscription.SubscriptionManager
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class PlanOption(
    val id: String,
    val title: String,
    val duration: String,
    val price: String,
    val tag: String? = null,
    val isPopular: Boolean = false
)

@Composable
fun SubscriptionPlanModal(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    checkoutUrl: String = "https://xubilasappshub.xubilaswebdevcorp.shop/pricing"
) {
    if (!isVisible) return

    val context = LocalContext.current
    val isPremium by SubscriptionManager.isPremium.collectAsState()
    val isExpired by SubscriptionManager.isExpired.collectAsState()
    val currentPlan by SubscriptionManager.subscriptionPlan.collectAsState()
    val liveVipConfig by SubscriptionManager.vipConfig.collectAsState()

    // Default static fallback plans
    val defaultPlans = listOf(
        PlanOption(
            id = "vip_1m",
            title = "1 Month VIP Pass",
            duration = "30 Days Access",
            price = "৳50",
            tag = "STARTER"
        ),
        PlanOption(
            id = "vip_3m",
            title = "3 Months VIP Pass",
            duration = "90 Days Access",
            price = "৳120",
            tag = "POPULAR",
            isPopular = true
        ),
        PlanOption(
            id = "vip_1y",
            title = "1 Year VIP Access",
            duration = "365 Days Access",
            price = "৳350",
            tag = "BEST VALUE (SAVE 50%)"
        )
    )

    // Merge live plans from website API
    val dynamicPlans = liveVipConfig?.pricingPlans?.map { plan ->
        PlanOption(
            id = plan.id,
            title = plan.name,
            duration = plan.duration,
            price = "৳${plan.priceBDT}",
            tag = plan.badge,
            isPopular = plan.isPopular
        )
    }

    val plans = if (!dynamicPlans.isNullOrEmpty()) dynamicPlans else defaultPlans
    var selectedPlanIndex by remember { mutableIntStateOf(if (plans.size > 1) 1 else 0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SpaceBlack),
                border = BorderStroke(
                    1.5.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFD700),
                            Color(0xFFFF8C00),
                            Color(0xFF2C1A30)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = "VIP",
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "VIP Subscription",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF232B3E), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Current Status Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    isPremium -> Color(0xFF1B3828)
                                    isExpired -> Color(0xFF3B1818)
                                    else -> Color(0xFF2C1A30)
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                when {
                                    isPremium -> Color(0xFF4CAF50)
                                    isExpired -> Color(0xFFFF4444).copy(alpha = 0.8f)
                                    else -> Color(0xFFFFB300).copy(alpha = 0.5f)
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        isPremium -> Icons.Default.Verified
                                        isExpired -> Icons.Default.Star
                                        else -> Icons.Default.Star
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isPremium -> Color(0xFF4CAF50)
                                        isExpired -> Color(0xFFFF5252)
                                        else -> Color(0xFFFFD700)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Current Status",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = when {
                                            isPremium -> "VIP Premium Active ($currentPlan)"
                                            isExpired -> "Subscription Expired - Please Renew"
                                            else -> "Free Member (Episode 1 Only)"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isPremium -> Color(0xFF4CAF50)
                                            isExpired -> Color(0xFFFF5252)
                                            else -> Color(0xFFFFD700)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choose Your VIP Plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Plan selection cards
                    val safeIndex = selectedPlanIndex.coerceIn(0, (plans.size - 1).coerceAtLeast(0))
                    plans.forEachIndexed { index, plan ->
                        val isSelected = safeIndex == index
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedPlanIndex = index },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF241C35) else Color(0xFF161B26)
                            ),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) Color(0xFFFFD700) else Color(0xFF2D3748)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                if (isSelected) Color(0xFFFFD700) else Color.Transparent,
                                                CircleShape
                                            )
                                            .border(2.dp, if (isSelected) Color(0xFFFFD700) else Color.Gray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = plan.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                            if (plan.tag != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                             if (plan.isPopular) Color(0xFFFF8C00) else Color(0xFF3B82F6),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = plan.tag,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = plan.duration,
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = plan.price,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) Color(0xFFFFD700) else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Benefits List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10141E), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Included with VIP Membership:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val features = listOf(
                            "🔓 Unlock ALL episodes for Series, Movies & Anime",
                            "🚫 100% Ad-Free (No interstitial or banner ads)",
                            "⚡ 4K Ultra HD & 1080p Fast Streaming Servers",
                            "💾 Fast Unlimited In-App Video Downloads",
                            "👑 Special VIP Badge on your Profile"
                        )
                        features.forEach { feat ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = feat,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    // Live Payment Gateways display if available
                    val paymentGateways = liveVipConfig?.paymentGateways
                    if (paymentGateways != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF141923),
                            border = BorderStroke(1.dp, Color(0xFF242E42)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Supported Payment Methods:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    paymentGateways.bkash?.let {
                                        Text("bKash: ${it.number}", fontSize = 10.sp, color = Color.White)
                                    }
                                    paymentGateways.nagad?.let {
                                        Text("Nagad: ${it.number}", fontSize = 10.sp, color = Color.White)
                                    }
                                    paymentGateways.rocket?.let {
                                        Text("Rocket: ${it.number}", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Button
                    val chosenPlan = plans[safeIndex]
                    Button(
                        onClick = {
                            val urlToOpen = if (checkoutUrl.isNotBlank()) checkoutUrl else "https://xubilasappshub.xubilaswebdevcorp.shop/pricing"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700),
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExpired) "Renew Now (${chosenPlan.price})" else "Subscribe Now (${chosenPlan.price})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Instant activation via Website Admin Panel. Support available 24/7.",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
