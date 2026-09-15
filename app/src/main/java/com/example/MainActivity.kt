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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
            com.example.security.SecurityGuard.applyScreenProtection(this)
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

        // Global Fan Code Lock check from Admin Panel
        if (config.isFanCodeLocked && config.fancodeCode.isNotBlank() && !isAppUnlockedWithFanCode) {
            GlobalFanCodeLockScreen(
                viewModel = viewModel,
                onRetry = { viewModel.fetchAppControlConfig() }
            )
            return
        }

        config.notice?.let { notice ->
            AppNoticeDialog(
                notice = notice,
                onDismiss = { viewModel.dismissAppNotice() }
            )
        }
    }

    val showPremiumPaywall by viewModel.showPremiumPaywall.collectAsState()
    if (showPremiumPaywall) {
        val config = appControlConfig
        PremiumPaywallDialog(
            title = config?.premiumPaywallTitle ?: "VIP Premium Subscription Required",
            message = config?.premiumPaywallMessage ?: "This content or tab is reserved for Premium Subscribers. Please purchase a subscription to continue.",
            buttonText = config?.premiumPaywallButtonText ?: "Buy Subscription Now",
            buttonUrl = config?.premiumPaywallButtonUrl ?: "",
            viewModel = viewModel,
            userProfile = userProfile,
            onDismiss = { viewModel.triggerPremiumPaywall(false) }
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

    LaunchedEffect(selectedAudioIndex) {
        com.example.ui.theme.AppTranslation.applyAppLocale(context, selectedAudioIndex)
    }

    // Create navigation items matching the screenshot
    val navItems = remember(isAdmin, selectedAudioIndex) {
        val baseList = mutableListOf(
            NavigationNavItem(
                title = com.example.ui.theme.AppTranslation.getString("home", selectedAudioIndex),
                icon = Icons.Default.Home,
                testTag = "tab_home"
            ),
            NavigationNavItem(
                title = com.example.ui.theme.AppTranslation.getString("browse", selectedAudioIndex),
                icon = Icons.Outlined.CalendarToday,
                testTag = "tab_browse"
            ),
            NavigationNavItem(
                title = "Air",
                icon = Icons.Default.Tv,
                testTag = "tab_settings"
            ),
            NavigationNavItem(
                title = com.example.ui.theme.AppTranslation.getString("downloads", selectedAudioIndex),
                icon = Icons.Outlined.Download,
                testTag = "tab_downloads"
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(mainBgColor),
        bottomBar = {
            AnimatedVisibility(
                visible = isNavBarVisible && !isFullScreen && !isInPipMode && !isLandscape,
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
                    // Crossfade Transitions between navigation tabs
                    Crossfade(
                        targetState = selectedTabIndex,
                        label = "TabTransition"
                    ) { targetIndex ->
                        when (targetIndex) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToPlayer = { navigateToTab(2) }, // Auto-switch to player tab when channel clicked
                                onNavigateToSettings = { navigateToTab(4) },
                                onNavigateToAirTab = { navigateToTab(2) },
                                onNavigateToMediaTab = { category ->
                                    viewModel.setSelectedMediaCategory(category)
                                    navigateToTab(1)
                                },
                                onBackPress = performBackNavigation,
                                isHeaderVisible = isNavBarVisible
                            )
                            1 -> MediaHubScreen(
                                viewModel = viewModel,
                                onNavigateToPlayer = { navigateToTab(2) },
                                onNavigateToAirTab = { viewModel.setSelectedTabIndex(0) },
                                isHeaderVisible = isNavBarVisible
                            )
                            2 -> Box(Modifier.fillMaxSize()) // Player rendered above
                            3 -> DownloadLibraryScreen(
                                viewModel = viewModel,
                                onBack = performBackNavigation
                            )
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
                    val isPlayerTab = selectedTabIndex == 2
                    val mainActivityContext = androidx.compose.ui.platform.LocalContext.current

                    if (isPlayerTab) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PlayerScreen(
                                viewModel = viewModel,
                                isMiniPlayer = false,
                                onMiniPlayerToggle = {
                                    // Move current player stream to a floating window and return to home tab
                                    viewModel.popCurrentToFloating()
                                    viewModel.setSelectedTabIndex(0)
                                    Toast.makeText(mainActivityContext, "Minimized to Floating Multi-View Player", Toast.LENGTH_SHORT).show()
                                },
                                isInPipMode = isInPipMode,
                                onBackPress = performBackNavigation
                            )
                        }
                    } else {
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

@Composable
fun AppNoticeDialog(
    notice: com.example.ui.viewmodel.AppNotice,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = { if (notice.isDismissible) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13111C)),
            border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFF6B00).copy(alpha = 0.6f), Color(0xFF8B5CF6).copy(alpha = 0.4f))))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
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
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!notice.imageUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = notice.imageUrl,
                        contentDescription = "Notice Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = Color(0xFFB4B4C0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (notice.isDismissible) {
                        OutlinedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.dp, Color(0xFF3B3B48)),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
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
            }
        }
    }
}

@Composable
fun GlobalFanCodeLockScreen(
    viewModel: com.example.ui.viewmodel.StreamViewModel,
    onRetry: () -> Unit
) {
    var enteredPasscode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090B14),
                        Color(0xFF0D0B1A),
                        Color(0xFF000000)
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141420)),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFF6B00).copy(alpha = 0.5f), Color(0xFF7C3AED).copy(alpha = 0.3f)))),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFF6B00).copy(alpha = 0.2f), Color(0xFFFF8800).copy(alpha = 0.1f))),
                            CircleShape
                        )
                        .border(1.5.dp, Color(0xFFFF6B00), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Fan Code Lock",
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Fan Code Access",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Enter the security Fan Code from the Admin Panel to unlock access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA1A1AA),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = enteredPasscode,
                    onValueChange = {
                        enteredPasscode = it
                        passcodeError = false
                    },
                    label = { Text("Security Fan Code") },
                    placeholder = { Text("Enter code...", color = Color.DarkGray) },
                    isError = passcodeError,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6B00),
                        unfocusedBorderColor = Color(0xFF2E2E3A),
                        focusedLabelColor = Color(0xFFFF6B00),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1B1B28),
                        unfocusedContainerColor = Color(0xFF1B1B28)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (passcodeError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Incorrect Fan Code! Please check and try again.",
                        color = Color(0xFFFF4D4D),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val success = viewModel.unlockAppWithFanCode(enteredPasscode)
                        if (!success) {
                            passcodeError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock App", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun PremiumPaywallDialog(
    title: String,
    message: String,
    buttonText: String,
    buttonUrl: String,
    viewModel: StreamViewModel,
    userProfile: com.example.ui.viewmodel.UserProfile?,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFFD700).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Premium VIP",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                RedeemCodeSection(viewModel, userProfile)

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (buttonUrl.isNotBlank()) {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(buttonUrl)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text(buttonText, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
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

