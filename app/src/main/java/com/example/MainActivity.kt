package com.example

import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.clickable
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat


import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.ui.components.LaunchAdOverlayScreen
import com.example.ui.components.FloatingDownloadButton
import com.example.ui.components.FloatingHomaiButton
import com.example.ui.components.GlowCapsuleNavigationBar
import com.example.ui.components.HomaiChatSheet
import com.example.ui.components.MultiFloatingPlayerOverlay
import com.example.ui.components.RedeemCodeSection
import com.example.ui.components.NavigationNavItem
import com.example.ui.screens.*
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.StreamViewModel
import com.example.ui.viewmodel.UiState
import com.example.update.AppUpdateDialog
import kotlinx.coroutines.flow.collectLatest

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.activity.viewModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: StreamViewModel by viewModels()
    private val _isInPipMode = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            com.example.network.AiAppSmoothnessController.boostThreadPriority()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        try {
            val splashScreen = installSplashScreen()
            var isSplashKeepOn = true
            lifecycleScope.launch {
                val start = System.currentTimeMillis()
                // Keep splash until initial remote suspension check finishes or max 700ms timeout
                while (System.currentTimeMillis() - start < 700) {
                    if (viewModel.isInitialControlChecked.value) break
                    kotlinx.coroutines.delay(50)
                }
                isSplashKeepOn = false
            }
            splashScreen.setKeepOnScreenCondition { isSplashKeepOn }
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                try {
                    val iconView = splashScreenView.iconView
                    val mainView = splashScreenView.view

                    if (iconView != null && mainView != null) {
                        val iconScaleX = android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f, 4f)
                        val iconScaleY = android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f, 4f)
                        val iconAlpha = android.animation.PropertyValuesHolder.ofFloat(android.view.View.ALPHA, 1f, 0f)
                        val viewAlpha = android.animation.PropertyValuesHolder.ofFloat(android.view.View.ALPHA, 1f, 0f)

                        val iconAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(iconView, iconScaleX, iconScaleY, iconAlpha).apply {
                            duration = 300L
                            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                        }

                        val viewAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(mainView, viewAlpha).apply {
                            duration = 300L
                            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                            addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    try { splashScreenView.remove() } catch (e: Throwable) { e.printStackTrace() }
                                }
                            })
                        }

                        android.animation.AnimatorSet().apply {
                            playTogether(iconAnimator, viewAnimator)
                            start()
                        }
                    } else {
                        splashScreenView.remove()
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    try { splashScreenView.remove() } catch (ex: Throwable) { ex.printStackTrace() }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            com.example.security.SecurityGuard.allowScreenCapture(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            val themeIndex by viewModel.themeIndex.collectAsState()
            MyApplicationTheme(themeIndex = themeIndex) {
                val isInPipMode by _isInPipMode.collectAsState()
                MainAppPortal(viewModel, isInPipMode)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlayingActive = viewModel.activeChannel.value != null || 
                                 viewModel.activeMediaItem.value != null || 
                                 viewModel.isMediaPlaying.value
            if (isPlayingActive && viewModel.autoPip.value) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
        viewModel.setInPipMode(isInPictureInPictureMode)
        
        // If exiting PiP mode and not returning to foreground (lifecycle not resumed), pause.
        if (!isInPictureInPictureMode && lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.setMediaPlaying(false)
            viewModel.setPlayerPlaying(false)
        }
    }
}

@Composable
fun MainAppPortal(viewModel: StreamViewModel, isInPipMode: Boolean = false) {
    val userProfile by viewModel.userProfile.collectAsState()
    val updateInfo by viewModel.updateInfoState.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val activeMediaItem by viewModel.activeMediaItem.collectAsState()
    val isPlayerPlaying by viewModel.isPlayerPlaying.collectAsState()
    val isMediaPlaying by viewModel.isMediaPlaying.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Keep phone screen awake while playing any Live TV channel or media stream
    DisposableEffect(activeChannel, activeMediaItem, isPlayerPlaying, isMediaPlaying) {
        val window = activity?.window
        val isPlaybackActive = (activeChannel != null && isPlayerPlaying) ||
                              (activeMediaItem != null && isMediaPlaying) ||
                              activeChannel != null ||
                              activeMediaItem != null
        if (isPlaybackActive) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Automatic Notification Permission Request for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.checkForAppUpdates(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Observe Super Admin Real-time Telemetry Notifications (Toast Alert triggers)
    LaunchedEffect(Unit) {
        viewModel.adminNotifications.collectLatest { notificationMessage ->
            Toast.makeText(context, notificationMessage, Toast.LENGTH_LONG).show()
        }
    }

    // Security Integrity Guard: Check for rooted device or active network packet sniffer / proxy
    val isRooted = remember { com.example.security.SecurityGuard.isDeviceRooted(context) }
    val isProxyActive = remember { com.example.security.SecurityGuard.isProxyOrVpnActive(context) }

    if (isRooted || isProxyActive) {
        val violationReason = if (isRooted) "Rooted device / Magisk binary detected." else "Active proxy or network packet sniffer detected."
        com.example.ui.screens.SecurityViolationScreen(
            title = "Security Check Failed",
            message = "Access is blocked due to high-security protection ($violationReason). Please disable root, Magisk, or packet inspection proxies to continue.",
            onExitApp = { (context as? android.app.Activity)?.finish() }
        )
        return
    }

    // Display AppsHub Update Dialog if an update is available
    updateInfo?.let { info ->
        AppUpdateDialog(
            updateInfo = info,
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    val appControlConfig by viewModel.appControlConfig.collectAsState()
    val isCheckingSuspension by viewModel.isCheckingSuspension.collectAsState()
    val isAppUnlockedWithFanCode by viewModel.isAppUnlockedWithFanCode.collectAsState()
    val showLaunchAdOverlay by viewModel.showLaunchAdOverlay.collectAsState()
    val vipModalNotice by com.example.subscription.SubscriptionManager.vipConfig.collectAsState()

    val activeNotice = remember(appControlConfig, vipModalNotice) {
        val config = appControlConfig
        if (config != null) {
            config.notice ?: run {
                val vNotice = vipModalNotice?.modalNotice
                if (vNotice != null && (vNotice.title.isNotBlank() || vNotice.subtitle.isNotBlank())) {
                    com.example.ui.viewmodel.AppNotice(
                        title = vNotice.title.ifBlank { "Special Announcement" },
                        message = vNotice.subtitle,
                        imageUrl = null,
                        buttonText = if (!vNotice.supportWhatsApp.isNullOrBlank()) "Contact Admin" else null,
                        buttonUrl = if (!vNotice.supportWhatsApp.isNullOrBlank()) "https://wa.me/${vNotice.supportWhatsApp?.replace("+", "")?.replace(" ", "")?.trim()}" else null,
                        isDismissible = true
                    )
                } else null
            }
        } else null
    }

    // Full screen Pre-Splash / Pre-Lock Ad or Poster Overlay
    val launchAd = appControlConfig?.launchAdOverlay
    if (showLaunchAdOverlay && launchAd != null && launchAd.enabled && launchAd.mediaUrl.isNotBlank()) {
        LaunchAdOverlayScreen(
            config = launchAd,
            onDismiss = { viewModel.dismissLaunchAdOverlay() },
            onOpenLink = { url ->
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to open launch ad url: $url", e)
                }
                viewModel.dismissLaunchAdOverlay()
            }
        )
        return
    }

    // Full screen kill-switch / suspension screen if enabled remotely
    appControlConfig?.let { config ->
        if (config.isAppSuspended) {
            AppSuspendedScreen(
                config = config,
                isChecking = isCheckingSuspension,
                onRetry = { viewModel.fetchAppControlConfig() }
            )
            return
        }

        // Global Fan Code Lock check from Admin Panel (enforced on splash/startup if isFanCodeSplashLocked is enabled)
        if (config.isFanCodeLocked && config.isFanCodeSplashLocked && config.fancodeCode.isNotBlank() && !isAppUnlockedWithFanCode) {
            GlobalFanCodeLockScreen(
                viewModel = viewModel,
                onRetry = { viewModel.fetchAppControlConfig() }
            )
            return
        }
    }

    val showPremiumPaywall by viewModel.showPremiumPaywall.collectAsState()
    var showMainLoginSheet by remember { mutableStateOf(false) }

    if (showPremiumPaywall) {
        val config = appControlConfig
        PremiumPaywallDialog(
            title = config?.premiumPaywallTitle ?: "VIP Premium Subscription Required",
            message = config?.premiumPaywallMessage ?: "This content or tab is reserved for Premium Subscribers. Please purchase a subscription to continue.",
            buttonText = config?.premiumPaywallButtonText ?: "Buy Subscription Now",
            buttonUrl = config?.premiumPaywallButtonUrl ?: "",
            viewModel = viewModel,
            userProfile = userProfile,
            onOpenLogin = {
                viewModel.triggerPremiumPaywall(false)
                showMainLoginSheet = true
            },
            onDismiss = { viewModel.triggerPremiumPaywall(false) }
        )
    }

    if (showMainLoginSheet) {
        com.example.ui.screens.SignInBottomSheet(
            viewModel = viewModel,
            onDismiss = { showMainLoginSheet = false }
        )
    }

    // Logged In/Guest Portal - Main Stream Layout
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val tabBackStack = remember { mutableStateListOf<Int>() }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    val navigateToTab: (Int) -> Unit = { targetIndex ->
        val tabNames = mapOf(
            0 to "home",
            1 to "movies",
            2 to "series",
            3 to "sports",
            4 to "anime",
            5 to "livetv",
            6 to "downloads",
            7 to "profile"
        )
        val targetName = tabNames[targetIndex] ?: ""
        val lockedTabs = appControlConfig?.lockedTabs ?: emptyList()
        val premiumCats = appControlConfig?.premiumCategories ?: emptyList()
        val isUserVip = viewModel.isUserPremium(userProfile?.email)

        if (!isUserVip && (lockedTabs.contains(targetName) || lockedTabs.contains(targetIndex.toString()) || premiumCats.contains(targetName))) {
            viewModel.triggerPremiumPaywall(true)
        } else if (selectedTabIndex != targetIndex) {
            if (tabBackStack.isEmpty() || tabBackStack.last() != selectedTabIndex) {
                tabBackStack.add(selectedTabIndex)
            }
            viewModel.setSelectedTabIndex(targetIndex)
        }
    }

    val isAdmin = userProfile?.isSuperAdmin == true
    val isFullScreen by viewModel.isFullScreen.collectAsState()
    LaunchedEffect(isFullScreen) {
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val showDownloadLibrary by viewModel.showDownloadLibraryGlobal.collectAsState()

    // Unified Back Navigation Handler (System navigation gesture + top app bar)
    val performBackNavigation: () -> Unit = {
        if (viewModel.isHomaiSheetVisible.value) {
            viewModel.closeHomaiChat()
        } else if (isFullScreen) {
            viewModel.setFullScreen(false)
        } else if (selectedTabIndex != 0) {
            if (tabBackStack.isNotEmpty()) {
                val previousTab = tabBackStack.removeAt(tabBackStack.lastIndex)
                viewModel.setSelectedTabIndex(previousTab)
            } else {
                viewModel.setSelectedTabIndex(0)
            }
        } else if (selectedPlaylist != null) {
            viewModel.clearSelectedPlaylist()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                activity?.finish()
            } else {
                lastBackPressTime = currentTime
                android.widget.Toast.makeText(context, "Double tap back to exit", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = true) {
        performBackNavigation()
    }

    val selectedAudioIndex by viewModel.audioIndex.collectAsState()
    val browseSlotType by viewModel.browseSlotType.collectAsState()
    val airSlotType by viewModel.airSlotType.collectAsState()
    val downloadsSlotType by viewModel.downloadsSlotType.collectAsState()

    LaunchedEffect(selectedAudioIndex) {
        com.example.ui.theme.AppTranslation.applyAppLocale(context, selectedAudioIndex)
    }

    // Helper to resolve slot navigation item
    fun getSlotNavItem(slotType: String, defaultTitle: String, defaultIcon: androidx.compose.ui.graphics.vector.ImageVector, tag: String): NavigationNavItem {
        return when (slotType) {
            "Browse" -> NavigationNavItem(
                title = com.example.ui.theme.AppTranslation.getString("browse", selectedAudioIndex),
                icon = Icons.Outlined.Dashboard,
                testTag = tag
            )
            "Air" -> NavigationNavItem(
                title = "Air",
                icon = Icons.Default.Tv,
                testTag = tag
            )
            "Live" -> NavigationNavItem(
                title = "Live",
                icon = Icons.Default.LiveTv,
                testTag = tag
            )
            "Sports" -> NavigationNavItem(
                title = "Sports",
                icon = Icons.Default.SportsSoccer,
                testTag = tag
            )
            "Master Anime", "Anime" -> NavigationNavItem(
                title = "Anime",
                icon = Icons.Default.AutoAwesome,
                testTag = tag
            )
            "Airing" -> NavigationNavItem(
                title = "Airing",
                icon = Icons.Default.Movie,
                testTag = tag
            )
            "Feeds" -> NavigationNavItem(
                title = "Feeds",
                icon = Icons.Default.DynamicFeed,
                testTag = tag
            )
            "Downloads" -> NavigationNavItem(
                title = com.example.ui.theme.AppTranslation.getString("downloads", selectedAudioIndex),
                icon = Icons.Outlined.Download,
                testTag = tag
            )
            else -> NavigationNavItem(
                title = defaultTitle,
                icon = defaultIcon,
                testTag = tag
            )
        }
    }

    // Create navigation items matching the configured slots
    val navItems = remember(isAdmin, selectedAudioIndex, browseSlotType, airSlotType, downloadsSlotType) {
        val baseList = mutableListOf(
            NavigationNavItem(
                title = com.example.ui.theme.AppTranslation.getString("home", selectedAudioIndex),
                icon = Icons.Default.Home,
                testTag = "tab_home"
            ),
            getSlotNavItem(
                slotType = browseSlotType,
                defaultTitle = com.example.ui.theme.AppTranslation.getString("browse", selectedAudioIndex),
                defaultIcon = Icons.Outlined.Dashboard,
                tag = "tab_browse"
            ),
            getSlotNavItem(
                slotType = airSlotType,
                defaultTitle = "Air",
                defaultIcon = Icons.Default.Tv,
                tag = "tab_air"
            ),
            getSlotNavItem(
                slotType = downloadsSlotType,
                defaultTitle = com.example.ui.theme.AppTranslation.getString("downloads", selectedAudioIndex),
                defaultIcon = Icons.Outlined.Download,
                tag = "tab_downloads"
            ),
            NavigationNavItem(
                title = com.example.ui.theme.AppTranslation.getString("profile", selectedAudioIndex),
                icon = Icons.Outlined.Person,
                testTag = "tab_profile"
            )
        )
        if (isAdmin) {
            baseList.add(
                NavigationNavItem(
                    title = com.example.ui.theme.AppTranslation.getString("admin", selectedAudioIndex),
                    icon = Icons.Default.AdminPanelSettings,
                    testTag = "tab_admin"
                )
            )
        }
        baseList
    }

    // Navigation Bar Scroll Auto-Hide Logic
    var isNavBarVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -15f) {
                    // Scrolling down -> hide header and floating navbar instantly & smoothly
                    if (isNavBarVisible) isNavBarVisible = false
                } else if (delta > 15f) {
                    // Scrolling up -> show header and floating navbar instantly & smoothly
                    if (!isNavBarVisible) isNavBarVisible = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = consumed.y
                if (delta < -2f) {
                    if (isNavBarVisible) isNavBarVisible = false
                } else if (delta > 2f) {
                    if (!isNavBarVisible) isNavBarVisible = true
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    // Always re-show navbar whenever tab or selected playlist changes
    LaunchedEffect(selectedTabIndex, selectedPlaylist) {
        isNavBarVisible = true
    }

    // Handle index out of bounds safe-guards when logging out admin
    LaunchedEffect(isAdmin) {
        if (!isAdmin && selectedTabIndex >= navItems.size) {
            viewModel.setSelectedTabIndex(0)
        }
    }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            viewModel.setSelectedTabIndex(2)
        }
    }

    if (isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            PlayerScreen(
                viewModel = viewModel,
                isInPipMode = true,
                onBackPress = performBackNavigation
            )
        }
        return
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val mainBgColor = if (isSystemDark) SpaceBlack else Color(0xFFF5F5F7)

    val currentSelectedSlotType = when (selectedTabIndex) {
        1 -> browseSlotType
        2 -> airSlotType
        3 -> downloadsSlotType
        else -> ""
    }
    val isBottomNavTabHidden = currentSelectedSlotType == "Feeds" || currentSelectedSlotType == "Airing"

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(mainBgColor),
        bottomBar = {
            AnimatedVisibility(
                visible = isNavBarVisible && !isFullScreen && !isInPipMode && !isLandscape && !isBottomNavTabHidden,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Inline Special Announcement (Stays directly above Navigation Bar, no overlap, edge-to-edge with no gap)
                        val notice = activeNotice
                        if (notice != null) {
                            AppNoticeBottomPanel(
                                notice = notice,
                                onDismiss = { viewModel.dismissAppNotice() }
                            )
                        }

                        val isUserVip = viewModel.isUserPremium(userProfile?.email)
                        if (appControlConfig?.isAdsEnabled == true && !isUserVip) {
                            NonPremiumAdBanner(
                                title = appControlConfig?.adTitle ?: "Sponsored: Upgrade to VIP to Remove Ads",
                                clickUrl = appControlConfig?.adClickUrl ?: "",
                                onRemoveAdsClick = { viewModel.triggerPremiumPaywall(true) }
                            )
                        }

                        GlowCapsuleNavigationBar(
                            selectedIndex = selectedTabIndex,
                            items = navItems,
                            isDark = isSystemDark,
                            onItemSelect = { index ->
                                if (selectedTabIndex == index) {
                                    viewModel.triggerTabReselect(index)
                                    when (index) {
                                        0 -> {
                                            viewModel.clearSelectedPlaylist()
                                            viewModel.setPlaylistSearchQuery("")
                                        }
                                        1 -> {
                                            viewModel.setMediaSearchQuery("")
                                            viewModel.setSelectedMediaCategory("All")
                                        }
                                        2 -> {
                                            if (isFullScreen) viewModel.setFullScreen(false)
                                        }
                                        3 -> {
                                            viewModel.setChannelSearchQuery("")
                                        }
                                    }
                                } else {
                                    navigateToTab(index)
                                }
                            }
                        )
                    }
                }
            }
        },
        contentWindowInsets = if (isFullScreen || isInPipMode) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mainBgColor)
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left Adaptive Sidebar for Landscape Orientation (Horizontal Navigation Rail)
                AnimatedVisibility(
                    visible = isLandscape && isNavBarVisible && !isFullScreen && !isInPipMode && !(selectedTabIndex == 4 && showDownloadLibrary),
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                ) {
                    com.example.ui.components.GlowSidebar(
                        selectedIndex = selectedTabIndex,
                        items = navItems,
                        isDark = isSystemDark,
                        onItemSelect = { index ->
                            if (selectedTabIndex == index) {
                                viewModel.triggerTabReselect(index)
                                when (index) {
                                    0 -> {
                                        viewModel.clearSelectedPlaylist()
                                        viewModel.setPlaylistSearchQuery("")
                                    }
                                    1 -> {
                                        viewModel.setMediaSearchQuery("")
                                        viewModel.setSelectedMediaCategory("All")
                                    }
                                    2 -> {
                                        if (isFullScreen) viewModel.setFullScreen(false)
                                    }
                                    3 -> {
                                        viewModel.setChannelSearchQuery("")
                                    }
                                }
                            } else {
                                navigateToTab(index)
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(mainBgColor)
                        .padding(
                            if (isFullScreen || isInPipMode) {
                                PaddingValues(0.dp)
                            } else if (isLandscape) {
                                PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = innerPadding.calculateBottomPadding()
                                )
                            } else if (selectedTabIndex == 0 || selectedTabIndex == 1 || selectedTabIndex == 2 || selectedTabIndex == 3 || selectedTabIndex == 4) {
                                PaddingValues(bottom = innerPadding.calculateBottomPadding())
                            } else {
                                innerPadding
                            }
                        )
                ) {
                    // Helper Composable to render any slot destination dynamically
                    @Composable
                    fun RenderNavigationSlotDestination(slotType: String) {
                        when (slotType) {
                            "Airing" -> {
                                AiringFeedScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { navigateToTab(2) },
                                    isHeaderVisible = isNavBarVisible
                                )
                            }
                            "Air" -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    PlayerScreen(
                                        viewModel = viewModel,
                                        isMiniPlayer = false,
                                        onMiniPlayerToggle = {
                                            viewModel.popCurrentToFloating()
                                            viewModel.setSelectedTabIndex(0)
                                            Toast.makeText(context, "Minimized to Floating Multi-View Player", Toast.LENGTH_SHORT).show()
                                        },
                                        isInPipMode = isInPipMode,
                                        onBackPress = performBackNavigation
                                    )
                                }
                            }
                            "Live" -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { navigateToTab(2) },
                                    onNavigateToSettings = { navigateToTab(4) },
                                    onNavigateToAirTab = { navigateToTab(2) },
                                    onNavigateToMediaTab = { category ->
                                        viewModel.setSelectedMediaCategory(category)
                                        navigateToTab(1)
                                    },
                                    onBackPress = performBackNavigation,
                                    isHeaderVisible = isNavBarVisible
                                )
                            }
                            "Sports" -> {
                                SportsHubScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { navigateToTab(2) },
                                    onBack = performBackNavigation
                                )
                            }
                            "Master Anime", "Anime" -> {
                                com.example.ui.components.MasterAnimeBrowserScreen(
                                    onBack = performBackNavigation
                                )
                            }
                            "Downloads" -> {
                                DownloadLibraryScreen(
                                    viewModel = viewModel,
                                    onBack = performBackNavigation
                                )
                            }
                            "Feeds" -> {
                                com.example.ui.screens.DiscoverFeedsScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { navigateToTab(2) },
                                    onBackPress = performBackNavigation,
                                    isHeaderVisible = isNavBarVisible
                                )
                            }
                            else -> {
                                val config = appControlConfig
                                if (config != null && config.isFanCodeLocked && config.isFanCodeTabLocked && config.fancodeCode.isNotBlank() && !isAppUnlockedWithFanCode) {
                                    GlobalFanCodeLockScreen(
                                        viewModel = viewModel,
                                        onRetry = { viewModel.fetchAppControlConfig() },
                                        onBackPress = { viewModel.setSelectedTabIndex(0) }
                                    )
                                } else {
                                    MediaHubScreen(
                                        viewModel = viewModel,
                                        onNavigateToPlayer = { navigateToTab(2) },
                                        onNavigateToAirTab = { viewModel.setSelectedTabIndex(0) },
                                        isHeaderVisible = isNavBarVisible
                                    )
                                }
                            }
                        }
                    }

                    // Crossfade Transitions between navigation tabs
                    Crossfade(
                        targetState = selectedTabIndex,
                        label = "TabTransition"
                    ) { targetIndex ->
                        when (targetIndex) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToPlayer = { navigateToTab(2) },
                                onNavigateToSettings = { navigateToTab(4) },
                                onNavigateToAirTab = { navigateToTab(2) },
                                onNavigateToMediaTab = { category ->
                                    viewModel.setSelectedMediaCategory(category)
                                    navigateToTab(1)
                                },
                                onBackPress = performBackNavigation,
                                isHeaderVisible = isNavBarVisible
                            )
                            1 -> RenderNavigationSlotDestination(slotType = browseSlotType)
                            2 -> RenderNavigationSlotDestination(slotType = airSlotType)
                            3 -> RenderNavigationSlotDestination(slotType = downloadsSlotType)
                            4 -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToPlayer = { navigateToTab(2) },
                                isHeaderVisible = isNavBarVisible
                            )
                            5 -> {
                                if (isAdmin) {
                                    AdminScreen(viewModel = viewModel)
                                } else {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }

                    // Floating In-App Player Overlay & Multi-View PIP
                    val currentSelectedSlotType = when (selectedTabIndex) {
                        1 -> browseSlotType
                        2 -> airSlotType
                        3 -> downloadsSlotType
                        else -> ""
                    }
                    val isCurrentlyViewingAirPlayer = currentSelectedSlotType == "Air"

                    if (!isCurrentlyViewingAirPlayer) {
                        // Display multiple draggable floating stream windows on top of browsing content
                        MultiFloatingPlayerOverlay(viewModel = viewModel)
                    }
                }
            }

            // Floating Download Button Overlay - Separated, Small and Semi-Transparent
            val activeDownloads by com.example.download.MediaDownloader.activeDownloads.collectAsState()
            var isFloatingDlDismissed by remember { mutableStateOf(false) }

            LaunchedEffect(activeDownloads.size) {
                if (activeDownloads.isNotEmpty()) {
                    isFloatingDlDismissed = false
                }
            }

            AnimatedVisibility(
                visible = activeDownloads.isNotEmpty() && !isFloatingDlDismissed && !isFullScreen && !isInPipMode && !isLandscape,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp)
            ) {
                FloatingDownloadButton(
                    downloads = activeDownloads,
                    onClick = {
                        viewModel.setShowDownloadLibraryGlobal(true)
                        viewModel.setSelectedTabIndex(4) // Switches to settings tab immediately
                    },
                    onDismiss = {
                        isFloatingDlDismissed = true
                    }
                )
            }
        }
    }

    val isHomaiSheetVisible by viewModel.isHomaiSheetVisible.collectAsState()
    if (isHomaiSheetVisible) {
        HomaiChatSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.closeHomaiChat() },
            onPlayChannel = { channel ->
                viewModel.closeHomaiChat()
                viewModel.setActiveChannel(channel)
                navigateToTab(2)
            },
            onPlayMedia = { media ->
                viewModel.closeHomaiChat()
                viewModel.playMediaItem(media)
                navigateToTab(2)
            }
        )
    }

    var showSubscriptionPlanGlobal by remember { mutableStateOf(false) }

    // Global Interstitial Ad Overlay (only triggers for non-premium users)
    com.example.ui.components.InterstitialAdOverlay(
        onOpenSubscriptionPlan = { showSubscriptionPlanGlobal = true }
    )

    // Global Subscription Plan Modal
    com.example.ui.components.SubscriptionPlanModal(
        isVisible = showSubscriptionPlanGlobal,
        onDismiss = { showSubscriptionPlanGlobal = false }
    )
}

@Composable
fun AppSuspendedScreen(
    config: com.example.ui.viewmodel.AppControlConfig,
    isChecking: Boolean = false,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E0A0A),
                        Color(0xFF0F0505),
                        Color(0xFF000000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative glowing circle background
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF3B30).copy(alpha = 0.12f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D0D).copy(alpha = 0.95f)),
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFFFF3B30).copy(alpha = 0.6f), Color(0xFFFF6B00).copy(alpha = 0.3f))))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header with pulse aura
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFF3B30).copy(alpha = 0.25f), Color(0xFFFF5252).copy(alpha = 0.1f))),
                            CircleShape
                        )
                        .border(1.5.dp, Color(0xFFFF3B30), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Service Suspended",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x33FF3B30),
                    border = BorderStroke(1.dp, Color(0x66FF3B30))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFFFF453A), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MAINTENANCE / SUSPENDED",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = config.suspensionTitle.ifBlank { "Service Temporarily Unavailable" },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = config.suspensionMessage.ifBlank { "The application is currently undergoing critical maintenance. Please check back later." },
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = Color(0xFFB0B0B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onRetry,
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF453A),
                        disabledContainerColor = Color(0x80FF453A)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Checking Status...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNoticeDialog(
    notice: com.example.ui.viewmodel.AppNotice,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF141416) else Color(0xFFF9F9FA)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) Color(0xFFB4B4C0) else Color(0xFF636366)
    val borderColor = if (isDark) Color(0xFF3B3B48) else Color(0xFFE5E5EA)

    ModalBottomSheet(
        onDismissRequest = { if (notice.isDismissible) onDismiss() },
        containerColor = bgColor,
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = Color(0xFFFF6B00).copy(alpha = 0.5f)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF6B00).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Announcement",
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "SPECIAL ANNOUNCEMENT",
                            color = Color(0xFFFF8800),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                if (notice.isDismissible) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = subTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!notice.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 230.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = notice.imageUrl,
                        contentDescription = "Notice Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 230.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                color = subTextColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (notice.isDismissible) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("Dismiss", fontSize = 13.sp)
                    }
                }

                if (!notice.buttonUrl.isNullOrBlank() && !notice.buttonText.isNullOrBlank()) {
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(notice.buttonUrl)
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(if (notice.isDismissible) 1.2f else 1f).height(42.dp)
                    ) {
                        Text(notice.buttonText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun AppNoticeBottomPanel(
    notice: com.example.ui.viewmodel.AppNotice,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF141417) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) Color(0xFFB4B4C0) else Color(0xFF636366)
    val borderColor = if (isDark) Color(0xFF2C2C32) else Color(0xFFE5E5EA)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF6B00).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Announcement",
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "SPECIAL ANNOUNCEMENT",
                            color = Color(0xFFFF8800),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                if (notice.isDismissible) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = subTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Notice Poster / Thumbnail Banner with heightened dimensions & Fit contentScale
            if (!notice.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = notice.imageUrl,
                        contentDescription = "Notice Poster",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 240.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                color = textColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp, fontSize = 13.sp),
                color = subTextColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (notice.isDismissible) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("Dismiss", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!notice.buttonUrl.isNullOrBlank() && !notice.buttonText.isNullOrBlank()) {
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(notice.buttonUrl)
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(if (notice.isDismissible) 1.2f else 1f).height(42.dp)
                    ) {
                        Text(notice.buttonText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun GlobalFanCodeLockScreen(
    viewModel: com.example.ui.viewmodel.StreamViewModel,
    onRetry: () -> Unit,
    onBackPress: (() -> Unit)? = null
) {
    androidx.activity.compose.BackHandler(enabled = onBackPress != null) {
        onBackPress?.invoke()
    }

    var enteredPasscode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf(false) }
    var showGetCodeModal by remember { mutableStateOf(false) }
    var showGetCodeOptionsModal by remember { mutableStateOf(false) }
    var showLoginSheet by remember { mutableStateOf(false) }

    val appControlConfig by viewModel.appControlConfig.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()

    var lastCheckedProfileEmail by remember { mutableStateOf<String?>(null) }

    // Check if logged in user is in Authorized VIP list from Admin Panel
    androidx.compose.runtime.LaunchedEffect(userProfile) {
        val currentEmail = userProfile?.email?.trim()
        if (!currentEmail.isNullOrEmpty() && currentEmail != lastCheckedProfileEmail) {
            lastCheckedProfileEmail = currentEmail
            val isVip = viewModel.isUserPremium(currentEmail)
            if (isVip) {
                viewModel.unlockAppWithFanCode("AUTO_VIP_USER")
                Toast.makeText(context, "Welcome VIP User! Access granted.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "You're not a VIP user! Please apply FanCode or buy subscription.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val bannerUrl = appControlConfig?.fancodeBannerUrl?.ifBlank { null }
        ?: "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?q=80&w=1000&auto=format&fit=crop"

    val subscribeUrl = appControlConfig?.fancodeWebUrl?.ifBlank { null }
        ?: appControlConfig?.premiumPaywallButtonUrl?.ifBlank { null }
        ?: "https://homeair.pages.dev/vip"

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val pageBg = if (isDark) Color(0xFF121214) else Color(0xFFFFF9F2)
    val cardBg = if (isDark) Color(0xFF1E1E24) else Color(0xFFFFF6EE)
    val cardBorder = if (isDark) Color(0xFF2C2C36) else Color(0xFFFFE0CC)
    val textPrimary = if (isDark) Color.White else Color(0xFF1E1E2C)
    val textSecondary = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val inputBg = if (isDark) Color(0xFF141418) else Color.White
    val badgeBg = if (isDark) Color(0xFF2C2C36) else Color(0xFFFFEAD0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
                // Top Orange Hero Banner Section matching image.png exactly with Calligraphic bottom curve
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF512F),
                                    Color(0xFFFF8800)
                                )
                            )
                        )
                ) {
                    // Admin Banner Image Overlay if configured or default image matching image.png
                    val isCustomBanner = appControlConfig?.fancodeBannerUrl?.isNotBlank() == true
                    coil.compose.AsyncImage(
                        model = bannerUrl,
                        contentDescription = "FanCode Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isCustomBanner) {
                                        listOf(
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Black.copy(alpha = 0.85f)
                                        )
                                    } else {
                                        listOf(
                                            Color(0xFFFF512F).copy(alpha = 0.82f),
                                            Color(0xFFFF8800).copy(alpha = 0.88f)
                                        )
                                    }
                                )
                            )
                    )

                    // Right-side calligraphic sports silhouettes & diagonal speed lines matching image.png
                    if (!isCustomBanner) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(160.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.15f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsBasketball,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.22f),
                                    modifier = Modifier
                                        .size(120.dp)
                                        .padding(end = 16.dp)
                                )
                            }
                        }
                    }

                    // Content Text Column (Matching image.png typography exactly with shadow for beautiful readability)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "REDEEM CODE",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                letterSpacing = 3.sp,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 3f),
                                    blurRadius = 4f
                                )
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "More Sports. More Action.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1.5f),
                                    blurRadius = 2f
                                )
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Get access to live sports, exclusive content, and premium features with FanCode.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            color = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )
                    }

                    // Back/Close Button for tab/overlay navigation control
                    if (onBackPress != null) {
                        IconButton(
                            onClick = onBackPress,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Fancode Apply Header Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFFF6B00), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ConfirmationNumber,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "FanCode Pass",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Enter your FanCode to get special benefits and unlock premium content.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Input Box with Apply Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(inputBg, RoundedCornerShape(12.dp))
                                    .border(1.2.dp, Color(0xFFFF6B00), RoundedCornerShape(12.dp))
                                    .padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (enteredPasscode.isEmpty()) {
                                        Text(
                                            text = "Enter FanCode",
                                            color = Color(0xFF9CA3AF),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = enteredPasscode,
                                        onValueChange = {
                                            enteredPasscode = it
                                            passcodeError = false
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textPrimary, fontWeight = FontWeight.Bold),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Button(
                                    onClick = {
                                        val success = viewModel.unlockAppWithFanCode(enteredPasscode)
                                        if (!success) {
                                            passcodeError = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) Color(0xFFFF6B00).copy(alpha = 0.25f) else Color(0xFFFFEAD0)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Apply", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold)
                                }
                            }

                            if (passcodeError) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Incorrect FanCode! Please verify and try again.",
                                    color = Color(0xFFFF4D4D),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4 Feature Badges in a Row (Matching image.png)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Live Matches & Events
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(badgeBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Live Matches\n& Events",
                                style = MaterialTheme.typography.labelSmall,
                                color = textPrimary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }

                        // 2. Exclusive Content
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(badgeBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Exclusive\nContent",
                                style = MaterialTheme.typography.labelSmall,
                                color = textPrimary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }

                        // 3. Watch on Any Device
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(badgeBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Watch on\nAny Device",
                                style = MaterialTheme.typography.labelSmall,
                                color = textPrimary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }

                        // 4. Premium Access
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(badgeBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Premium\nAccess",
                                style = MaterialTheme.typography.labelSmall,
                                color = textPrimary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Subscribe Now Gradient Button
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(subscribeUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF512F),
                                            Color(0xFFFF8800)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Subscribe Now",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Get Code Button (Controlled from Website Admin Panel)
                    val isGetCodeEnabled = appControlConfig?.isFanCodeGetCodeEnabled != false
                    if (isGetCodeEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showGetCodeOptionsModal = true },
                            border = BorderStroke(1.2.dp, Color(0xFFFF6B00).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFFF6B00).copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("fancode_get_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Get FanCode",
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Get FanCode",
                                color = Color(0xFFFF6B00),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Footer Login Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already have a FanCode account? ",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                        androidx.compose.foundation.text.ClickableText(
                            text = androidx.compose.ui.text.AnnotatedString("Login"),
                            onClick = {
                                showLoginSheet = true
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFFF6B00),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

    if (showGetCodeOptionsModal) {
        FanCodeGetCodeOptionsModal(
            viewModel = viewModel,
            onApplyRedeemCode = {
                showGetCodeOptionsModal = false
                showGetCodeModal = true
            },
            onDismiss = { showGetCodeOptionsModal = false }
        )
    }

    if (showGetCodeModal) {
        FanCodeGetCodeModal(
            viewModel = viewModel,
            userProfile = userProfile,
            onOpenLogin = {
                showGetCodeModal = false
                showLoginSheet = true
            },
            onDismiss = { showGetCodeModal = false }
        )
    }

    if (showLoginSheet) {
        com.example.ui.screens.SignInBottomSheet(
            viewModel = viewModel,
            onDismiss = { showLoginSheet = false }
        )
    }
}

@Composable
fun GoldenTicketGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(100.dp, 80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating sparkles / confetti around
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFD700),
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 2.dp)
        )
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.BottomStart)
                .offset(x = 2.dp, y = (-8).dp)
        )
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFE082),
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 6.dp)
        )

        // Main Golden Ticket Card (Tilted 12 degrees)
        Box(
            modifier = Modifier
                .size(82.dp, 54.dp)
                .graphicsLayer { rotationZ = 12f }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFD54F),
                            Color(0xFFFF8F00)
                        )
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Crown inside ticket
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )

            // Percent badge on ticket bottom right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(28.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFE65100), Color(0xFFFF3D00))
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(1.2.dp, Color.White, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FanCodeGetCodeOptionsModal(
    viewModel: com.example.ui.viewmodel.StreamViewModel,
    onApplyRedeemCode: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appControlConfig by viewModel.appControlConfig.collectAsState()

    val telegramUrl = appControlConfig?.fancodeTelegramUrl?.ifBlank { null }
        ?: "https://t.me/HomeAirTv"

    val webUrl = appControlConfig?.fancodeWebUrl?.ifBlank { null }
        ?: appControlConfig?.premiumPaywallButtonUrl?.ifBlank { null }
        ?: "https://homeair.pages.dev/vip"

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val modalBg = if (isDark) Color(0xFF141418) else Color(0xFFFFF9F2)
    val textPrimary = if (isDark) Color.White else Color(0xFF1E1E2C)
    val textSecondary = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = modalBg,
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = Color(0xFFFF6B00).copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFF6B00), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Get FanCode",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "Choose how you'd like to get your code",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Get via Telegram Channel
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(telegramUrl)
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open Telegram link", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24A1DE)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Get via Telegram Channel",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Get via Official Website
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(webUrl)
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open Web link", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Website",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Get via Official Website",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. I Have a Redeem Code / Apply
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onApplyRedeemCode()
                },
                border = BorderStroke(1.2.dp, Color(0xFFFF6B00)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalOffer,
                        contentDescription = "Apply Code",
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Apply Redeem Code",
                        color = Color(0xFFFF6B00),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "Close",
                    color = Color(0xFF71717A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FanCodeGetCodeModal(
    viewModel: com.example.ui.viewmodel.StreamViewModel,
    userProfile: com.example.ui.viewmodel.UserProfile?,
    onOpenLogin: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var enteredCode by remember { mutableStateOf("") }
    var applyError by remember { mutableStateOf(false) }
    var applySuccess by remember { mutableStateOf(false) }

    val appControlConfig by viewModel.appControlConfig.collectAsState()
    val subscribeUrl = appControlConfig?.fancodeWebUrl?.ifBlank { null }
        ?: appControlConfig?.premiumPaywallButtonUrl?.ifBlank { null }
        ?: "https://homeair.pages.dev/vip"

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val modalBg = if (isDark) Color(0xFF141418) else Color(0xFFFFF9F2)
    val cardBg = if (isDark) Color(0xFF1E1E24) else Color(0xFFFFF6EE)
    val cardBorder = if (isDark) Color(0xFF2C2C36) else Color(0xFFFFE0CC)
    val textPrimary = if (isDark) Color.White else Color(0xFF1E1E2C)
    val textSecondary = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val inputBg = if (isDark) Color(0xFF282830) else Color(0xFFFFF9F2)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = modalBg,
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = Color(0xFFFF6B00).copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Top Orange Hero Banner Section matching image.png exactly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF512F),
                                Color(0xFFFF8800)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "REDEEM CODE",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                letterSpacing = 2.5.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Apply Redeem Code",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Got a special code? Enter it below to unlock exciting offers and premium benefits!",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    GoldenTicketGraphic()
                }
            }

            // Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (userProfile == null) {
                    // Sign-in gate card when not signed in
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sign In Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Please sign in to apply redeem codes and unlock exclusive FanCode access.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenLogin()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Sign In Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Redeem Code Box (Only appears when signed in)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFFFF6B00).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalOffer,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B00),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Enter Redeem Code",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Type your code here (e.g. FAN10, VIP20, etc.)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Input Field Box
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .background(inputBg, RoundedCornerShape(12.dp))
                                    .border(1.2.dp, Color(0xFFFF6B00).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (enteredCode.isEmpty()) {
                                        Text(
                                            text = "Enter code",
                                            color = Color(0xFF9CA3AF),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = enteredCode,
                                        onValueChange = {
                                            enteredCode = it
                                            applyError = false
                                            applySuccess = false
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textPrimary, fontWeight = FontWeight.Bold),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (applyError) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Invalid code! Please check and try again.",
                                    color = Color(0xFFFF4D4D),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (applySuccess) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Success! Redeem code applied successfully.",
                                    color = Color(0xFF10B981),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Apply Button
                    Button(
                        onClick = {
                            val success = viewModel.unlockAppWithFanCode(enteredCode)
                            if (success) {
                                applySuccess = true
                                Toast.makeText(context, "Redeem code applied successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                applyError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF512F),
                                            Color(0xFFFF8800)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Apply",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // How it works? Card (Matching image.png exactly)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6EE)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFE0CC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFFF6B00), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "How it works?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E2C)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val bulletPoints = listOf(
                            "Enter your redeem code in the box above.",
                            "Click Apply to unlock the offer.",
                            "Enjoy exclusive benefits on Fancode!"
                        )

                        bulletPoints.forEach { point ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF71717A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subscribe Button (as requested: "ar tar niche subscribe and close button")
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(subscribeUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF512F),
                                        Color(0xFFFF8800)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subscribe Now",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "Close",
                        color = Color(0xFF71717A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PremiumPaywallDialog(
    title: String,
    message: String,
    buttonText: String,
    buttonUrl: String,
    viewModel: StreamViewModel,
    userProfile: com.example.ui.viewmodel.UserProfile?,
    onOpenLogin: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var enteredCode by remember { mutableStateOf("") }
    var applyError by remember { mutableStateOf(false) }
    var applySuccess by remember { mutableStateOf(false) }

    val appControlConfig by viewModel.appControlConfig.collectAsState()
    val subscribeUrl = buttonUrl.ifBlank { null }
        ?: appControlConfig?.fancodeWebUrl?.ifBlank { null }
        ?: appControlConfig?.premiumPaywallButtonUrl?.ifBlank { null }
        ?: "https://homeair.pages.dev/vip"

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFFF9F2),
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = Color(0xFFFF6B00).copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Top Orange Hero Banner Section matching image.png exactly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF512F),
                                Color(0xFFFF8800)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "REDEEM CODE",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                letterSpacing = 2.5.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title.ifBlank { "Apply Redeem Code" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.ifBlank { "Got a special code? Enter it below to unlock exciting offers and premium benefits!" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    GoldenTicketGraphic()
                }
            }

            // Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (userProfile == null) {
                    // Sign-in gate card when not signed in
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6EE)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFE0CC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sign In Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E2C)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Please sign in to apply redeem codes and unlock exclusive FanCode access.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF71717A),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenLogin()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Sign In Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Redeem Code Box (Only appears when signed in)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFE0CC)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFFFFEAD0), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalOffer,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B00),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Enter Redeem Code",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E1E2C)
                                    )
                                    Text(
                                        text = "Type your code here (e.g. FAN10, VIP20, etc.)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF71717A)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Input Field Box
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .background(Color(0xFFFFF9F2), RoundedCornerShape(12.dp))
                                    .border(1.2.dp, Color(0xFFFFE0CC), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (enteredCode.isEmpty()) {
                                        Text(
                                            text = "Enter code",
                                            color = Color(0xFF9CA3AF),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = enteredCode,
                                        onValueChange = {
                                            enteredCode = it
                                            applyError = false
                                            applySuccess = false
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF1E1E2C), fontWeight = FontWeight.Bold),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (applyError) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Invalid code! Please check and try again.",
                                    color = Color(0xFFFF4D4D),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (applySuccess) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Success! Redeem code applied successfully.",
                                    color = Color(0xFF10B981),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Apply Button
                    Button(
                        onClick = {
                            val success = viewModel.unlockAppWithFanCode(enteredCode)
                            if (success) {
                                applySuccess = true
                                Toast.makeText(context, "Redeem code applied successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                applyError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF512F),
                                            Color(0xFFFF8800)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Apply",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // How it works? Card (Matching image.png exactly)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6EE)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFE0CC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFFF6B00), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "How it works?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E2C)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val bulletPoints = listOf(
                            "Enter your redeem code in the box above.",
                            "Click Apply to unlock the offer.",
                            "Enjoy exclusive benefits on Fancode!"
                        )

                        bulletPoints.forEach { point ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF71717A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subscribe Button
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(subscribeUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF512F),
                                        Color(0xFFFF8800)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = buttonText.ifBlank { "Subscribe Now" },
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "Close",
                        color = Color(0xFF71717A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NonPremiumAdBanner(
    title: String,
    clickUrl: String,
    onRemoveAdsClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        color = Color(0xFF18181B),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF27272A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable {
                if (clickUrl.isNotBlank()) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(clickUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFFFF6B00),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "SPONSORED",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.ifBlank { "Sponsored: Upgrade to VIP to Remove Ads" },
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Remove Ads",
                color = Color(0xFFFFD700),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRemoveAdsClick() }
            )
        }
    }
}

