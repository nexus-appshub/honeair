package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class NavigationNavItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String,
    val activeGradient: List<Color> = listOf(NeonCyan, NeonPurple)
)

@Suppress("DEPRECATION")
@Composable
fun GlowCapsuleNavigationBar(
    selectedIndex: Int,
    items: List<NavigationNavItem>,
    onItemSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()
) {
    val backgroundColor = if (isDark) Color(0xFF18181A) else Color.White
    val borderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val unselectedColor = if (isDark) Color(0xFFA1A1A6) else Color(0xFF757575)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("glow_capsule_navbar"),
        color = backgroundColor,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val isCenter = index == 2 // Center floating button (Settings / Player)

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.88f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "nav_item_scale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(color = Color(0xFF00E676), bounded = false)
                        ) {
                            onItemSelect(index)
                        }
                        .testTag(item.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isCenter) {
                            // Center highlighted compact circular button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isPressed) Color(0xFF00E676) else Color(0xFFFF6B00))
                                    .shadow(4.dp, CircleShape, spotColor = if (isPressed) Color(0xFF00E676) else Color(0xFFFF6B00)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isPressed) Color(0xFF00E676) else if (isSelected) Color(0xFFFF6B00) else unselectedColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(1.dp))

                        Text(
                            text = item.title,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected || isCenter || isPressed) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPressed) Color(0xFF00E676) else if (isSelected || isCenter) Color(0xFFFF6B00) else unselectedColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlowSidebar(
    selectedIndex: Int,
    items: List<NavigationNavItem>,
    onItemSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()
) {
    val backgroundColor = if (isDark) Color(0xFF18181A) else Color.White
    val borderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val unselectedColor = if (isDark) Color(0xFFA1A1A6) else Color(0xFF757575)

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(88.dp)
            .testTag("glow_sidebar"),
        color = backgroundColor,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp, 
            Brush.verticalGradient(listOf(borderColor, borderColor.copy(alpha = 0.5f)))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Branding Logo Icon at Top of Sidebar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NeonCyan, NeonPurple, NeonMagenta)
                        )
                    )
                    .padding(1.dp)
                    .background(backgroundColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Home Air TV",
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Items
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val isCenter = index == 2

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.88f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "sidebar_item_scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(color = Color(0xFF00E676), bounded = false)
                        ) {
                            onItemSelect(index)
                        }
                        .testTag(item.testTag + "_sidebar"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isCenter) {
                            // Highlighted center button
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isPressed) Color(0xFF00E676) else Color(0xFFFF6B00))
                                    .shadow(6.dp, CircleShape, spotColor = if (isPressed) Color(0xFF00E676) else Color(0xFFFF6B00)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isPressed) Color(0xFF00E676) else if (isSelected) Color(0xFFFF6B00) else unselectedColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected || isCenter || isPressed) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPressed) Color(0xFF00E676) else if (isSelected || isCenter) Color(0xFFFF6B00) else unselectedColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
