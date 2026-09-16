package com.example.ad

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.subscription.SubscriptionManager
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.Mrec
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * StartIoAdManager coordinates all ad operations for Start.io (StartApp) SDK.
 * Bypasses all ad requests immediately for users with active VIP premium subscription.
 */
object StartIoAdManager {
    private const val TAG = "StartIoAdManager"
    const val APP_ID = "208883567"
    const val PUBLISHER_ID = "110602603"

    // Set to true during active testing to enable official Start.io test ads
    var TEST_MODE = false

    private val isInitialized = AtomicBoolean(false)

    // Cooldown trackers for interstitial frequency capping
    private var lastInterstitialShownTime: Long = 0L
    private const val INTERSTITIAL_COOLDOWN_MS = 300_000L // 5 Minutes
    private var actionCountSinceLastAd = 0
    private const val ACTIONS_BEFORE_AD = 4 // Show ad every 4th channel change or movie play

    // Preloaded interstitial reference to avoid loading latency on stream click
    private var preloadedInterstitial: StartAppAd? = null

    /**
     * Initialize the Start.io SDK, disable default popups, and preload first ad.
     */
    fun init(context: Context, testMode: Boolean = false) {
        if (isInitialized.getAndSet(true)) return
        TEST_MODE = testMode

        try {
            Log.d(TAG, "Initializing Start.io SDK with App ID: $APP_ID, Test Mode: $TEST_MODE")
            
            // Disable default splash screen popup to avoid video stutter or interference
            StartAppAd.disableSplash()
            
            // Initialize StartApp SDK
            StartAppSDK.init(context, APP_ID, false)
            
            // Enable test ads mode if enabled
            StartAppSDK.setTestAdsEnabled(TEST_MODE)
            
            // Warm up/preload interstitial ad immediately for seamless UX
            preloadInterstitial(context.applicationContext)
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing Start.io SDK", e)
        }
    }

    /**
     * Preloads an interstitial ad in the background.
     */
    private fun preloadInterstitial(context: Context) {
        if (SubscriptionManager.isVipUser()) return
        try {
            val ad = StartAppAd(context)
            ad.loadAd(object : AdEventListener {
                override fun onReceiveAd(adInstance: Ad) {
                    Log.d(TAG, "Background interstitial ad preloaded successfully")
                    preloadedInterstitial = ad
                }

                override fun onFailedToReceiveAd(adInstance: Ad?) {
                    Log.e(TAG, "Failed to preload interstitial ad in background")
                    preloadedInterstitial = null
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Error preloading interstitial ad", e)
        }
    }

    /**
     * Displays a frequency-capped Interstitial Ad.
     * Enforces a 5-minute cooldown OR every 4th action (e.g. channel change / play movie) limit.
     */
    fun showInterstitialIfEligible(activity: Activity, placement: String = "default", onDismiss: () -> Unit) {
        // Immediate bypass for premium users
        if (SubscriptionManager.isVipUser()) {
            onDismiss()
            return
        }

        actionCountSinceLastAd++
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastInterstitialShownTime
        val isCooldownPassed = timePassed >= INTERSTITIAL_COOLDOWN_MS
        val isActionLimitReached = actionCountSinceLastAd >= ACTIONS_BEFORE_AD

        Log.d(TAG, "Interstitial check - Placement: $placement, ActionCount: $actionCountSinceLastAd/$ACTIONS_BEFORE_AD, Cooldown: ${timePassed / 1000}s/${INTERSTITIAL_COOLDOWN_MS / 1000}s")

        if (!isCooldownPassed && !isActionLimitReached) {
            Log.d(TAG, "Interstitial skipped due to frequency capping")
            onDismiss()
            return
        }

        // Try to show preloaded ad first, otherwise load a new one on-demand with fallback
        val adToShow = preloadedInterstitial
        if (adToShow != null && adToShow.isReady) {
            Log.d(TAG, "Showing preloaded Start.io Interstitial Ad")
            displayInterstitial(adToShow, onDismiss)
            // Reset counters
            lastInterstitialShownTime = System.currentTimeMillis()
            actionCountSinceLastAd = 0
            // Preload next one
            preloadInterstitial(activity.applicationContext)
        } else {
            Log.d(TAG, "Preloaded ad not ready, loading on-demand Interstitial")
            try {
                val newAd = StartAppAd(activity)
                newAd.loadAd(object : AdEventListener {
                    override fun onReceiveAd(adInstance: Ad) {
                        displayInterstitial(newAd, onDismiss)
                        lastInterstitialShownTime = System.currentTimeMillis()
                        actionCountSinceLastAd = 0
                        preloadInterstitial(activity.applicationContext)
                    }

                    override fun onFailedToReceiveAd(adInstance: Ad?) {
                        Log.e(TAG, "On-demand interstitial loading failed, proceeding directly to stream")
                        onDismiss()
                    }
                })
            } catch (e: Throwable) {
                Log.e(TAG, "Error loading on-demand interstitial", e)
                onDismiss()
            }
        }
    }

    private fun displayInterstitial(ad: StartAppAd, onDismiss: () -> Unit) {
        ad.showAd(object : AdDisplayListener {
            override fun adDisplayed(adInstance: Ad) {
                Log.d(TAG, "Interstitial displayed successfully")
            }

            override fun adNotDisplayed(adInstance: Ad) {
                Log.e(TAG, "Interstitial failed to display")
                onDismiss()
            }

            override fun adClicked(adInstance: Ad) {
                Log.d(TAG, "Interstitial ad clicked")
            }

            override fun adHidden(adInstance: Ad) {
                Log.d(TAG, "Interstitial ad dismissed by user")
                onDismiss()
            }
        })
    }

    /**
     * Displays a Rewarded Video Ad with success/failure callbacks.
     * Used to unlock premium servers, boosters, and download links.
     */
    fun showRewardedVideo(activity: Activity, onRewardEarned: () -> Unit, onFailed: (String) -> Unit) {
        if (SubscriptionManager.isVipUser()) {
            // VIP users get premium rewards immediately without watching ads
            onRewardEarned()
            return
        }

        try {
            val rewardedAd = StartAppAd(activity)
            val isRewarded = AtomicBoolean(false)

            rewardedAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    Log.d(TAG, "Rewarded video completed. Granting benefit.")
                    isRewarded.set(true)
                }
            })

            rewardedAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(adInstance: Ad) {
                    rewardedAd.showAd(object : AdDisplayListener {
                        override fun adDisplayed(adInstance: Ad) {
                            Log.d(TAG, "Rewarded video ad displayed")
                        }

                        override fun adNotDisplayed(adInstance: Ad) {
                            Log.e(TAG, "Rewarded video failed to display")
                            onFailed("Ad display failed")
                        }

                        override fun adClicked(adInstance: Ad) {
                            Log.d(TAG, "Rewarded video ad clicked")
                        }

                        override fun adHidden(adInstance: Ad) {
                            if (isRewarded.get()) {
                                onRewardEarned()
                            } else {
                                onFailed("Video was closed early")
                            }
                        }
                    })
                }

                override fun onFailedToReceiveAd(adInstance: Ad?) {
                    Log.e(TAG, "Failed to load rewarded video ad")
                    onFailed("Ad load failed")
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during rewarded video execution", e)
            onFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Programmatic MREC Banner Creator (300x250) for BottomSheets / Details view.
     */
    fun createMrecBanner(context: Context): View {
        return try {
            Mrec(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating programmatic Mrec", e)
            View(context) // Return empty placeholder to avoid crash
        }
    }

    /**
     * Programmatic Standard Banner Creator (320x50) for list views or players.
     */
    fun createStandardBanner(context: Context): View {
        return try {
            Banner(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating programmatic Banner", e)
            View(context)
        }
    }

    /**
     * Programmatic Native Ad Loader helper.
     * Fetches details of a single native ad (title, description, image, action)
     * so it can be rendered dynamically in Jetpack Compose.
     */
    fun loadNativeAd(context: Context, onAdLoaded: (NativeAdDetails) -> Unit, onAdFailed: () -> Unit) {
        if (SubscriptionManager.isVipUser()) {
            onAdFailed()
            return
        }

        try {
            val nativeAd = StartAppNativeAd(context)
            val preferences = NativeAdPreferences().apply {
                adsNumber = 1
                primaryImageSize = 3 // Extra large image size
            }

            nativeAd.loadAd(preferences, object : AdEventListener {
                override fun onReceiveAd(adInstance: Ad) {
                    val adsList = nativeAd.nativeAds
                    if (!adsList.isNullOrEmpty()) {
                        onAdLoaded(adsList[0])
                    } else {
                        onAdFailed()
                    }
                }

                override fun onFailedToReceiveAd(adInstance: Ad?) {
                    Log.e(TAG, "Failed to receive native ad")
                    onAdFailed()
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during native ad loading", e)
            onAdFailed()
        }
    }
}

/**
 * Jetpack Compose wrapper for Start.io Standard Banner Ads.
 */
@Composable
fun StartIoBannerAd(
    modifier: Modifier = Modifier,
    isMrec: Boolean = false
) {
    if (SubscriptionManager.isVipUser()) return

    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val adView = if (isMrec) {
                    StartIoAdManager.createMrecBanner(ctx)
                } else {
                    StartIoAdManager.createStandardBanner(ctx)
                }
                addView(adView)
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Highly customized Native Ad view designed in Material Design 3 for flawless UI integration.
 * Adapts beautifully to dark and light modes, and contains action button.
 */
@Composable
fun StartIoNativeAdView(
    modifier: Modifier = Modifier
) {
    if (SubscriptionManager.isVipUser()) return

    val context = LocalContext.current
    var adDetails by remember { mutableStateOf<NativeAdDetails?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        StartIoAdManager.loadNativeAd(
            context = context,
            onAdLoaded = { adDetails = it },
            onAdFailed = { loadFailed = true }
        )
    }

    if (loadFailed || adDetails == null) {
        // If ads fail or are not loaded yet, don't display empty cards
        return
    }

    val ad = adDetails!!

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        // Wrap with AndroidView so that Start.io can capture impressions and clicks via registerViewForInteraction
        AndroidView(
            factory = { ctx ->
                // Create custom view for Start.io interaction tracking
                val cardView = android.view.LayoutInflater.from(ctx).inflate(
                    android.R.layout.simple_list_item_1, null, false
                )
                // Register interaction
                ad.registerViewForInteraction(cardView)
                cardView
            },
            modifier = Modifier.size(0.dp) // Keep registration view hidden/zero-size
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Trigger click action programmatically when card is clicked
                    try {
                        val fakeView = View(context)
                        ad.registerViewForInteraction(fakeView)
                        fakeView.performClick()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Secondary image / Icon
                val iconBitmap = ad.secondaryImageBitmap
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = "Ad Icon",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (!ad.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ad.imageUrl,
                        contentDescription = "Ad Icon",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = ad.title ?: "Sponsored Content",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SPONSORED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Large Ad Image
            val mainBitmap = ad.imageBitmap
            if (mainBitmap != null) {
                Image(
                    bitmap = mainBitmap.asImageBitmap(),
                    contentDescription = "Ad Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (!ad.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ad.imageUrl,
                    contentDescription = "Ad Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = ad.description ?: "Tired of interruptions? Upgrade to VIP Premium now for high-speed streaming without ads.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 18.sp,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    try {
                        val fakeView = View(context)
                        ad.registerViewForInteraction(fakeView)
                        fakeView.performClick()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text(
                    text = ad.installs ?: "Install / Learn More",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
