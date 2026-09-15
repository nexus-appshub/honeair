package com.example

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

        config.notice?.let { notice ->
            AppNoticeDialog(
                notice = notice,
                onDismiss = { viewModel.dismissAppNotice() }
            )
        }
    }

    // Logged In/Guest Portal - Main Stream Layout
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val tabBackStack = remember { mutableStateListOf<Int>() }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    val navigateToTab: (Int) -> Unit = { targetIndex ->
        if (selectedTabIndex != targetIndex) {
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
                            2 -> PlayerScreen(
                                viewModel = viewModel,
                                isInPipMode = isInPipMode,
                                onBackPress = performBackNavigation
                            )
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
}

@Composable
fun AppSuspendedScreen(
    config: com.example.ui.viewmodel.AppControlConfig,
    isChecking: Boolean = false,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF09090B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color(0xFFFF3B30).copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, Color(0xFFFF3B30), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Access Blocked",
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x33FF3B30),
                border = BorderStroke(1.dp, Color(0x66FF3B30))
            ) {
                Text(
                    text = "MAINTENANCE MODE",
                    color = Color(0xFFFF453A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = config.suspensionTitle,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = config.suspensionMessage,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRetry,
                enabled = !isChecking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B00),
                    disabledContainerColor = Color(0x80FF6B00)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Checking Server Status...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh & Check Again", color = Color.White, fontWeight = FontWeight.Bold)
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
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            border = BorderStroke(1.dp, Color(0xFF27272A))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!notice.imageUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = notice.imageUrl,
                        contentDescription = "Notice Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Announcement",
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notice.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA1A1AA),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(notice.buttonText, color = Color.White)
                        }
                    }

                    if (notice.isDismissible) {
                        OutlinedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

