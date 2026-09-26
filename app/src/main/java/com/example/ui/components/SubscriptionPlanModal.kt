package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import com.example.data.api.GatewayInfo
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
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.StreamViewModel
import com.example.ui.viewmodel.UserProfile

data class PlanOption(
    val id: String,
    val title: String,
    val duration: String,
    val price: String,
    val tag: String? = null,
    val isPopular: Boolean = false
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPlanModal(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    checkoutUrl: String = "https://www.hmair.xyz/vip"
) {
    if (!isVisible) return

    val context = LocalContext.current
    val viewModel: StreamViewModel = viewModel()
    val userProfile by viewModel.userProfile.collectAsState()
    val isPremium by SubscriptionManager.isPremium.collectAsState()
    val isExpired by SubscriptionManager.isExpired.collectAsState()
    val currentPlan by SubscriptionManager.subscriptionPlan.collectAsState()
    val liveVipConfig by SubscriptionManager.vipConfig.collectAsState()

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val modalBg = if (isDark) SpaceBlack else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subTextColor = if (isDark) TextSecondary else Color(0xFF64748B)
    val sectionBg = if (isDark) Color(0xFF10141E) else Color(0xFFF1F5F9)
    val cardBg = if (isDark) Color(0xFF161B26) else Color(0xFFF8FAFC)
    val cardSelectedBg = if (isDark) Color(0xFF261D15) else Color(0xFFFFF7ED)
    val borderColor = if (isDark) Color(0xFF2D3748) else Color(0xFFE2E8F0)
    val iconBtnBg = if (isDark) Color(0xFF232B3E) else Color(0xFFE2E8F0)
    val iconBtnTint = if (isDark) Color.White else Color(0xFF1E293B)
    val accentOrange = Color(0xFFFF6B00)
    val accentGold = Color(0xFFFFB300)

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
            tag = "SAVE 50%"
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
    var showPaymentGuideStep by remember { androidx.compose.runtime.mutableStateOf(false) }
    var selectedPaymentMethod by remember { androidx.compose.runtime.mutableStateOf("bkash") }

    androidx.compose.runtime.LaunchedEffect(isVisible) {
        if (isVisible) {
            SubscriptionManager.fetchLiveVipConfig()
            viewModel.fetchAppControlConfig()
        }
    }
    
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = modalBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
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
                    if (showPaymentGuideStep) {
                        IconButton(
                            onClick = { showPaymentGuideStep = false },
                            modifier = Modifier
                                .size(30.dp)
                                .background(iconBtnBg, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = iconBtnTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    Brush.linearGradient(listOf(accentGold, accentOrange)),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "VIP",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showPaymentGuideStep) "Payment Guide" else "VIP Subscription",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textColor
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconBtnBg, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = iconBtnTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current Status Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            isPremium -> if (isDark) Color(0xFF1B3828) else Color(0xFFE8F5E9)
                            isExpired -> if (isDark) Color(0xFF3B1818) else Color(0xFFFFEBEE)
                            else -> if (isDark) Color(0xFF281E12) else Color(0xFFFFF8E1)
                        },
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        when {
                            isPremium -> Color(0xFF4CAF50)
                            isExpired -> Color(0xFFFF5252).copy(alpha = 0.8f)
                            else -> accentOrange.copy(alpha = 0.6f)
                        },
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                else -> accentOrange
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Current Status",
                                fontSize = 10.sp,
                                color = subTextColor
                            )
                            Text(
                                text = when {
                                    isPremium -> "VIP Premium Active ($currentPlan)"
                                    isExpired -> "Subscription Expired - Please Renew"
                                    else -> "Free Member (Episode 1 Only)"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isPremium -> Color(0xFF4CAF50)
                                    isExpired -> Color(0xFFFF5252)
                                    else -> if (isDark) accentOrange else Color(0xFFD95300)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val safeIndex = selectedPlanIndex.coerceIn(0, (plans.size - 1).coerceAtLeast(0))
            val chosenPlan = plans[safeIndex]

            if (!showPaymentGuideStep) {
                Text(
                    text = "Choose Your VIP Plan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Compact Plan selection cards
                plans.forEachIndexed { index, plan ->
                    val isSelected = safeIndex == index
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedPlanIndex = index },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) cardSelectedBg else cardBg
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) accentOrange else borderColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            if (isSelected) accentOrange else Color.Transparent,
                                            CircleShape
                                        )
                                        .border(1.5.dp, if (isSelected) accentOrange else Color.Gray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = plan.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = textColor
                                        )
                                        if (plan.tag != null) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (plan.isPopular) accentOrange else Color(0xFF3B82F6),
                                                        RoundedCornerShape(3.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = plan.tag,
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = plan.duration,
                                        fontSize = 10.sp,
                                        color = subTextColor
                                    )
                                }
                            }

                            Text(
                                text = plan.price,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = if (isSelected) accentOrange else textColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Compact Benefits List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(sectionBg, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Included with VIP Membership:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = accentOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val features = listOf(
                        "🔓 Unlock ALL episodes for Series, Movies & Anime",
                        "🚫 100% Ad-Free (No interstitial or banner ads)",
                        "⚡ 4K Ultra HD & 1080p Fast Streaming Servers",
                        "💾 Fast Unlimited In-App Video Downloads",
                        "👑 Special VIP Badge on your Profile"
                    )
                    features.forEach { feat ->
                        Row(
                            modifier = Modifier.padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = feat,
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                // Check if mobile payments are disabled via admin
                val isMobilePaymentEnabled = liveVipConfig?.isMobilePaymentEnabled != false
                
                if (isMobilePaymentEnabled) {
                    // Live Payment Gateways preview display if available
                    val paymentGateways = liveVipConfig?.paymentGateways
                    if (paymentGateways != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = sectionBg,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Available In-App Payment Methods:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentOrange
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    paymentGateways.bkash?.let {
                                        Text("bKash", fontSize = 9.sp, color = textColor, fontWeight = FontWeight.Bold)
                                    }
                                    paymentGateways.nagad?.let {
                                        Text("Nagad", fontSize = 9.sp, color = textColor, fontWeight = FontWeight.Bold)
                                    }
                                    paymentGateways.rocket?.let {
                                        Text("Rocket", fontSize = 9.sp, color = textColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val disabledNote = liveVipConfig?.mobilePaymentDisabledNote ?: "In-App mobile payments are currently disabled. Please click below to complete your payment securely on our official website."
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF2C1A30) else Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = "Notice", tint = Color(0xFFFF6B6B), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Notice", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = disabledNote,
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.9f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Button - Pay via Website
                Button(
                    onClick = {
                        val urlToOpen = liveVipConfig?.externalPaymentUrl ?: checkoutUrl
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentOrange,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pay via Website (${chosenPlan.price})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                // === PAYMENT INSTRUCTIONS VIEW ===
                val paymentGateways = liveVipConfig?.paymentGateways
                val merchantConfig = liveVipConfig?.merchantConfig
                
                // Default Fallback details if live config is empty
                val fallbackBkash = merchantConfig?.bkashNumber?.takeIf { it.isNotBlank() }?.let { GatewayInfo(number = it, type = merchantConfig.bkashType ?: "Personal") }
                    ?: GatewayInfo(number = "01722104387", type = "Personal")
                val fallbackNagad = merchantConfig?.nagadNumber?.takeIf { it.isNotBlank() }?.let { GatewayInfo(number = it, type = merchantConfig.nagadType ?: "Personal") }
                    ?: GatewayInfo(number = "01722104387", type = "Personal")
                val fallbackRocket = merchantConfig?.rocketNumber?.takeIf { it.isNotBlank() }?.let { GatewayInfo(number = it, type = merchantConfig.rocketType ?: "Personal") }
                    ?: GatewayInfo(number = "01722104387", type = "Personal")
                
                val activeBkash = paymentGateways?.bkash ?: fallbackBkash
                val activeNagad = paymentGateways?.nagad ?: fallbackNagad
                val activeRocket = paymentGateways?.rocket ?: fallbackRocket
                
                val selectedGateway = when (selectedPaymentMethod) {
                    "bkash" -> activeBkash
                    "nagad" -> activeNagad
                    "rocket" -> activeRocket
                    else -> activeBkash
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = sectionBg),
                    border = BorderStroke(1.dp, accentOrange.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Selected Plan: ${chosenPlan.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Amount to Send: ${chosenPlan.price}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = accentOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Select Payment Method:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Row of Payment Method Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val methods = listOf(
                        Triple("bkash", "bKash", Color(0xFFE2125B)),
                        Triple("nagad", "Nagad", Color(0xFFF15922)),
                        Triple("rocket", "Rocket", Color(0xFF8C3494))
                    )
                    methods.forEach { (id, name, color) ->
                        val isSelected = selectedPaymentMethod == id
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPaymentMethod = id },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) color.copy(alpha = 0.15f) else sectionBg
                            ),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) color else borderColor
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (isSelected) color else Color.Transparent, CircleShape)
                                        .border(1.5.dp, if (isSelected) color else Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isSelected) color else textColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Gateway Details (Number & Copy)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = sectionBg,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${selectedPaymentMethod.uppercase()} Number",
                                    fontSize = 10.sp,
                                    color = subTextColor
                                )
                                Text(
                                    text = selectedGateway.number,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor
                                )
                                Text(
                                    text = "Account Type: ${selectedGateway.type}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = accentOrange
                                )
                            }

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Payment Number", selectedGateway.number)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "${selectedPaymentMethod.uppercase()} Number Copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(iconBtnBg, RoundedCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Number",
                                    tint = iconBtnTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // How to pay Guidelines
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = sectionBg)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "How to Pay (পেমেন্ট নিয়মাবলী):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = accentOrange
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val sendMethod = if (selectedGateway.type.lowercase().contains("merchant")) "Make Payment" else "Send Money"
                        
                        Text(
                            text = "1. Copy the number displayed above.\n" +
                                   "2. Open your ${selectedPaymentMethod.capitalize()} wallet app.\n" +
                                   "3. Choose option: **$sendMethod**.\n" +
                                   "4. Send EXACTLY **${chosenPlan.price}** BDT to this number.\n" +
                                   "5. After success, COPY the **Transaction ID (TrxID)**.\n" +
                                   "6. Tap 'Submit via WhatsApp' below and message Admin the TrxID for instant VIP activation!",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.85f),
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // WhatsApp Admin Link & Action Button
                val rawWhatsapp = paymentGateways?.whatsapp
                    ?: merchantConfig?.whatsappNumber
                    ?: merchantConfig?.helplineNumber
                    ?: "+8801722104387"
                val whatsappUrl = if (rawWhatsapp.startsWith("http")) {
                    rawWhatsapp
                } else {
                    val cleanNum = rawWhatsapp.replace("+", "").replace(" ", "").trim()
                    "https://wa.me/$cleanNum?text=Hello%20Admin,%20I%20have%20sent%20${chosenPlan.price}%20BDT%20via%20${selectedPaymentMethod.capitalize()}%20for%20the%20VIP%20Premium%20Plan%20(${chosenPlan.title}).%20Please%20verify%20and%20activate%20my%20device."
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Submit TxID via WhatsApp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary backup Website Redirect
                OutlinedButton(
                    onClick = {
                        val urlToOpen = if (checkoutUrl.isNotBlank()) checkoutUrl else "https://www.hmair.xyz/vip"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, borderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                ) {
                    Text(
                         text = "Pay via Website",
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }

            RedeemCodeSection(viewModel, userProfile)

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Instant activation via Website Admin Panel. Support available 24/7.",
                fontSize = 9.sp,
                color = subTextColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RedeemCodeSection(
    viewModel: com.example.ui.viewmodel.StreamViewModel,
    userProfile: com.example.ui.viewmodel.UserProfile?
) {
    var codeText by remember { mutableStateOf("") }
    var redeemMessage by remember { mutableStateOf("") }
    var redeemSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val sectionBg = if (isDark) Color(0xFF141923) else Color(0xFFF8FAFC)
    val inputBg = if (isDark) Color(0xFF1C2434) else Color.White
    val borderColor = if (isDark) Color(0xFF2D3748) else Color(0xFFE2E8F0)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val accentOrange = Color(0xFFFF6B00)

    val isUserLoggedIn = userProfile != null
    val userEmail = userProfile?.email

    Spacer(modifier = Modifier.height(12.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = sectionBg,
        border = BorderStroke(1.dp, accentOrange.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = accentOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Apply Promo / Redeem Code",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = 12.sp),
                    cursorBrush = SolidColor(accentOrange),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .background(inputBg, RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    decorationBox = { innerTextField ->
                        if (codeText.isEmpty()) {
                            Text(
                                text = "Enter VIP / Promo Code...",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Button(
                    onClick = {
                        if (codeText.isNotBlank() && !isSubmitting) {
                            isSubmitting = true
                            redeemMessage = "Validating code..."
                            scope.launch {
                                val (success, message) = viewModel.applyRedeemCode(codeText, userEmail)
                                redeemSuccess = success
                                redeemMessage = message
                                if (success) {
                                    codeText = ""
                                }
                                isSubmitting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentOrange,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isSubmitting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            if (redeemMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = redeemMessage,
                    color = if (redeemSuccess == true) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
