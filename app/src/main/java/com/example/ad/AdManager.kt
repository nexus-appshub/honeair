package com.example.ad

import android.content.Context
import android.util.Log
import com.example.subscription.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdInfo(
    val title: String = "Sponsored Feature: VIP Ultra Stream",
    val description: String = "Upgrade to VIP to enjoy 4K streaming without interruptions.",
    val bannerUrl: String = "",
    val clickUrl: String = "https://xubilasappshub.xubilaswebdevcorp.shop/pricing",
    val actionText: String = "Learn More"
)

/**
 * AdManager coordinates ad-loading and interstitial display for non-premium users.
 * Automatically bypassed for users with VIP Premium status.
 */
object AdManager {
    private const val TAG = "AdManager"
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isInterstitialVisible = MutableStateFlow(false)
    val isInterstitialVisible: StateFlow<Boolean> = _isInterstitialVisible.asStateFlow()

    private val _currentAd = MutableStateFlow<AdInfo?>(null)
    val currentAd: StateFlow<AdInfo?> = _currentAd.asStateFlow()

    private var lastAdShownTimestamp: Long = 0L
    private const val MIN_AD_INTERVAL_MS = 90_000L // 1.5 minutes between interstitials

    private var dismissCallback: (() -> Unit)? = null

    init {
        // Start periodic background ad preloader check
        scope.launch {
            while (true) {
                delay(60_000) // Periodic check every 1 min
                if (!SubscriptionManager.isVipUser()) {
                    preloadAd()
                }
            }
        }
    }

    private fun preloadAd() {
        if (_currentAd.value == null) {
            _currentAd.value = AdInfo(
                title = "Unlock VIP Premium Cinema",
                description = "Get instant access to all locked episodes, high-speed 4K streaming, and 100% ad-free playback.",
                actionText = "Upgrade Now"
            )
        }
    }

    /**
     * Attempts to show an interstitial ad if the user is non-premium and cooldown has elapsed.
     */
    fun showInterstitialIfEligible(context: Context, placement: String = "general", onDismiss: () -> Unit) {
        // 1. If user is VIP, immediately skip ads
        if (SubscriptionManager.isVipUser()) {
            onDismiss()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAdShownTimestamp < MIN_AD_INTERVAL_MS) {
            Log.d(TAG, "Ad interval cooldown active for placement: $placement")
            onDismiss()
            return
        }

        // Trigger interstitial ad display
        lastAdShownTimestamp = currentTime
        dismissCallback = onDismiss
        _currentAd.value = AdInfo(
            title = "Special Sponsor Offer: VIP Cinema Pass",
            description = "Tired of ads? Upgrade to VIP to enjoy 4K Ultra HD & unlock all series episodes permanently.",
            actionText = "Get VIP Pass"
        )
        _isInterstitialVisible.value = true
    }

    fun dismissInterstitial() {
        _isInterstitialVisible.value = false
        val cb = dismissCallback
        dismissCallback = null
        cb?.invoke()
    }

    /**
     * Check if banner ads should be rendered in UI
     */
    fun shouldShowBannerAd(isRemoteAdsEnabled: Boolean): Boolean {
        if (SubscriptionManager.isVipUser()) return false
        return isRemoteAdsEnabled
    }
}
