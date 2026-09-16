package com.example.ad

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.subscription.SubscriptionManager
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
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

data class NativeAdData(
    val title: String,
    val description: String,
    val imageUrl: String,
    val callToAction: String = "Install Now",
    val rating: Float = 4.8f,
    val nativeAdDetails: NativeAdDetails? = null
)

/**
 * Centralized Start.io (StartApp) Ad Manager for Home Air TV.
 * Features a STRICT VIP / Premium User Bypass system:
 * - Paying users never see ads.
 * - Rewarded actions invoke immediate callback.
 * - Saves network bandwidth and memory by skipping ad preloads for VIP users.
 */
object StartIoAdManager {
    private const val TAG = "StartIoAdManager"

    const val START_IO_APP_ID = "208883567"
    const val START_IO_PUBLISHER_ID = "110602603"

    var TEST_MODE: Boolean = true
    var isUserPremiumOverride: Boolean? = false

    private var channelChangeCount = 0
    private var lastInterstitialTimeMs = 0L
    private const val CHANNEL_CHANGE_INTERVAL = 4
    private const val INTERSTITIAL_COOLDOWN_MS = 5 * 60 * 1000L

    private var isInitialized = false

    var isUserPremium: Boolean
        get() = isPremiumUser()
        set(value) {
            isUserPremiumOverride = value
        }

    fun isPremiumUser(): Boolean {
        return isUserPremiumOverride ?: SubscriptionManager.isVipUser()
    }

    fun init(context: Context, appId: String = START_IO_APP_ID, testMode: Boolean = TEST_MODE) {
        if (isPremiumUser()) {
            Log.d(TAG, "VIP user detected. Skipping Start.io SDK initialization.")
            return
        }

        if (isInitialized) return

        try {
            TEST_MODE = testMode
            StartAppSDK.init(context, appId, false)
            StartAppSDK.enableReturnAds(false)
            StartAppAd.disableSplash()
            
            if (testMode || TEST_MODE) {
                StartAppSDK.setTestAdsEnabled(true)
            }
            isInitialized = true
            Log.d(TAG, "Start.io SDK initialized for App ID: $appId (TestMode: ${TEST_MODE})")
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing Start.io SDK: ${e.message}", e)
        }
    }

    fun showSplashAd(activity: Activity, onCompleted: () -> Unit = {}) {
        if (isPremiumUser()) {
            Log.d(TAG, "VIP user detected. Skipping Splash Ad.")
            onCompleted()
            return
        }

        try {
            init(activity)
            val startAppAd = StartAppAd(activity)
            // AUTOMATIC mode for full-screen / splash ad delivery
            startAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    startAppAd.showAd(object : AdDisplayListener {
                        override fun adHidden(ad: Ad?) { onCompleted() }
                        override fun adDisplayed(ad: Ad?) {}
                        override fun adClicked(ad: Ad?) {}
                        override fun adNotDisplayed(ad: Ad?) { onCompleted() }
                    })
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.e(TAG, "Splash Ad Failed to load: ${ad?.errorMessage}")
                    onCompleted()
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during Splash Ad display: ${e.message}")
            onCompleted()
        }
    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit) {
        if (isPremiumUser()) {
            Log.d(TAG, "VIP user detected. Interstitial Ad bypassed.")
            onAdClosed()
            return
        }

        init(activity)
        channelChangeCount++
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastInterstitialTimeMs

        val isChannelCapReached = (channelChangeCount % CHANNEL_CHANGE_INTERVAL == 0)
        val isCooldownElapsed = (timeSinceLastAd >= INTERSTITIAL_COOLDOWN_MS)

        if (!isChannelCapReached && !isCooldownElapsed) {
            Log.d(TAG, "Interstitial frequency cap active.")
            onAdClosed()
            return
        }

        lastInterstitialTimeMs = currentTime

        try {
            val startAppAd = StartAppAd(activity)
            startAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    startAppAd.showAd(object : AdDisplayListener {
                        override fun adHidden(ad: Ad?) { onAdClosed() }
                        override fun adDisplayed(ad: Ad?) {}
                        override fun adClicked(ad: Ad?) {}
                        override fun adNotDisplayed(ad: Ad?) { onAdClosed() }
                    })
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    onAdClosed()
                }
            })
        } catch (e: Throwable) {
            onAdClosed()
        }
    }

    fun onChannelChanged(activity: Activity, onAdClosed: () -> Unit) {
        showInterstitial(activity, onAdClosed)
    }

    fun showRewardedVideo(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdFailed: () -> Unit = {}
    ) {
        if (isPremiumUser()) {
            Log.d(TAG, "VIP user detected. Granting Rewarded Feature instantly.")
            onRewardEarned()
            return
        }

        init(activity)

        try {
            var isRewardGranted = false
            val startAppAd = StartAppAd(activity)

            startAppAd.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    isRewardGranted = true
                    onRewardEarned()
                }
            })

            startAppAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    startAppAd.showAd(object : AdDisplayListener {
                        override fun adHidden(ad: Ad?) {
                            if (!isRewardGranted) onAdFailed()
                        }
                        override fun adDisplayed(ad: Ad?) {}
                        override fun adClicked(ad: Ad?) {}
                        override fun adNotDisplayed(ad: Ad?) { onAdFailed() }
                    })
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    onAdFailed()
                }
            })
        } catch (e: Throwable) {
            onAdFailed()
        }
    }

    fun loadBanner(container: ViewGroup, isMrec: Boolean = false) {
        if (isPremiumUser()) {
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        container.removeAllViews()

        try {
            init(container.context)
            val banner = if (isMrec) {
                Mrec(container.context, object : BannerListener {
                    override fun onReceiveAd(view: View?) { container.visibility = View.VISIBLE }
                    override fun onFailedToReceiveAd(view: View?) { container.visibility = View.GONE }
                    override fun onClick(view: View?) {}
                    override fun onImpression(view: View?) {}
                })
            } else {
                Banner(container.context, object : BannerListener {
                    override fun onReceiveAd(view: View?) { container.visibility = View.VISIBLE }
                    override fun onFailedToReceiveAd(view: View?) { container.visibility = View.GONE }
                    override fun onClick(view: View?) {}
                    override fun onImpression(view: View?) {}
                })
            }
            container.addView(banner)
        } catch (e: Throwable) {
            container.visibility = View.GONE
        }
    }

    fun loadNativeAd(
        context: Context,
        onAdLoaded: (NativeAdData) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isPremiumUser()) {
            onError("VIP Premium User")
            return
        }

        init(context)
        try {
            val startAppNativeAd = StartAppNativeAd(context)
            val nativePrefs = NativeAdPreferences().apply {
                adsNumber = 1
                isAutoBitmapDownload = true
                primaryImageSize = 2
            }

            startAppNativeAd.loadAd(nativePrefs, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    val nativeAds = startAppNativeAd.nativeAds
                    if (!nativeAds.isNullOrEmpty()) {
                        val details = nativeAds[0]
                        onAdLoaded(
                            NativeAdData(
                                title = details.title ?: "Sponsor Feature",
                                description = details.description ?: "Discover high-speed HD streaming servers.",
                                imageUrl = details.imageUrl ?: "",
                                callToAction = details.callToAction ?: "Install Now",
                                rating = details.rating,
                                nativeAdDetails = details
                            )
                        )
                    } else {
                        onError("No native ads returned")
                    }
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    onError(ad?.errorMessage ?: "Failed to load Native Ad")
                }
            })
        } catch (e: Throwable) {
            onError(e.message ?: "Error loading native ad")
        }
    }

    fun shouldShowAdAtPosition(position: Int): Boolean {
        if (isPremiumUser()) return false
        return position > 0 && (position + 1) % 8 == 0
    }
}

@Composable
fun StartIoBannerView(
    modifier: Modifier = Modifier,
    isMrec: Boolean = false
) {
    val isPremium = SubscriptionManager.isPremium.collectAsState().value || StartIoAdManager.isUserPremium

    if (isPremium) {
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    StartIoAdManager.loadBanner(this, isMrec = isMrec)
                }
            },
            update = { frameLayout ->
                StartIoAdManager.loadBanner(frameLayout, isMrec = isMrec)
            }
        )
    }
}

@Composable
fun StartIoNativeAdCard(
    modifier: Modifier = Modifier,
    onOpenSubscription: () -> Unit = {}
) {
    val isPremium = SubscriptionManager.isPremium.collectAsState().value || StartIoAdManager.isUserPremium

    if (isPremium) {
        Spacer(modifier = Modifier.size(0.dp))
        return
    }

    val context = LocalContext.current
    var adData by remember { mutableStateOf<NativeAdData?>(null) }

    LaunchedEffect(Unit) {
        StartIoAdManager.loadNativeAd(
            context = context,
            onAdLoaded = { data -> adData = data },
            onError = {}
        )
    }

    val currentAd = adData ?: NativeAdData(
        title = "Upgrade to VIP Unlimited",
        description = "Remove all ads, unlock 4K Ultra HD servers, and download movies directly.",
        imageUrl = "",
        callToAction = "Go VIP Now"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SPONSORED AD",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Hide Ads with VIP",
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpenSubscription() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentAd.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = currentAd.imageUrl,
                        contentDescription = "Ad Image",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentAd.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentAd.description,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (adData == null) {
                        onOpenSubscription()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = currentAd.callToAction,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
