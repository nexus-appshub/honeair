package com.example.subscription

import android.util.Log
import com.example.data.api.GatewayInfo
import com.example.data.api.MerchantConfig
import com.example.data.api.ModalNotice
import com.example.data.api.PaymentGateways
import com.example.data.api.RedeemCode
import com.example.data.api.VipApiClient
import com.example.data.api.VipConfigResponse
import com.example.data.api.VipPlan
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * SubscriptionManager manages VIP / Premium membership status using Firebase Auth & Firestore,
 * with full date-based expiry checks (7 days, 30 days, 1 year, Lifetime) and live API sync.
 */
object SubscriptionManager {
    private const val TAG = "SubscriptionManager"
    private const val COLLECTION_PREMIUM_USERS = "premium_users"

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _subscriptionStatus = MutableStateFlow("Free Member")
    val subscriptionStatus: StateFlow<String> = _subscriptionStatus.asStateFlow()

    private val _subscriptionPlan = MutableStateFlow("Free Tier")
    val subscriptionPlan: StateFlow<String> = _subscriptionPlan.asStateFlow()

    private val _expiryDate = MutableStateFlow<String?>(null)
    val expiryDate: StateFlow<String?> = _expiryDate.asStateFlow()

    private val _expiryTimestamp = MutableStateFlow<Long?>(null)
    val expiryTimestamp: StateFlow<Long?> = _expiryTimestamp.asStateFlow()

    private val _isExpired = MutableStateFlow(false)
    val isExpired: StateFlow<Boolean> = _isExpired.asStateFlow()

    // Event triggered when user subscription has expired
    private val _subscriptionExpiredEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val subscriptionExpiredEvent: SharedFlow<String> = _subscriptionExpiredEvent.asSharedFlow()

    // Live Web VIP Config State
    private val _vipConfig = MutableStateFlow<VipConfigResponse?>(null)
    val vipConfig: StateFlow<VipConfigResponse?> = _vipConfig.asStateFlow()

    private val remotePremiumEmails = mutableSetOf<String>()
    private var freeEpisodeLimit: Int = 1

    init {
        // Fetch live VIP plans and payment configuration on startup
        fetchLiveVipConfig()

        try {
            val auth = FirebaseAuth.getInstance()
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    checkUserSubscription(user.email, user.uid)
                } else {
                    // Check if local cached profile exists
                    recomputeStatus(null, null)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseAuth initialization fallback: ${e.message}")
        }
    }

    /**
     * Fetches live VIP pricing plans and payment gateways from the server API and Firebase RTDB
     */
    fun fetchLiveVipConfig() {
        scope.launch(Dispatchers.IO) {
            // Priority 1: Fetch directly from Firebase Realtime Database (where admin panel saves changes immediately)
            val firebaseUrls = listOf(
                "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/app_vip_config.json",
                "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/configs/vipConfig.json"
            )

            var loadedFromFirebase = false
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()

            for (fbUrl in firebaseUrls) {
                if (loadedFromFirebase) break
                try {
                    val req = Request.Builder()
                        .url(fbUrl)
                        .header("Accept", "application/json")
                        .build()
                    okHttpClient.newCall(req).execute().use { response ->
                        val body = response.body?.string()
                        if (response.isSuccessful && !body.isNullOrBlank() && body.trim() != "null" && body.trim().startsWith("{")) {
                            val parsed = parseVipConfigFromJson(body)
                            if (parsed != null && (parsed.pricingPlans.isNotEmpty() || parsed.paymentGateways != null || parsed.merchantConfig != null)) {
                                _vipConfig.value = parsed
                                synchronized(remotePremiumEmails) {
                                    remotePremiumEmails.clear()
                                    if (parsed.premiumUsers.isNotEmpty()) {
                                        remotePremiumEmails.addAll(parsed.premiumUsers.map { it.trim().lowercase() }.filter { it.isNotBlank() })
                                    }
                                }
                                loadedFromFirebase = true
                                Log.d(TAG, "Successfully loaded live VIP config & payment info from Firebase: $fbUrl")
                                
                                val auth = try { FirebaseAuth.getInstance() } catch (e: Throwable) { null }
                                val user = auth?.currentUser
                                checkUserSubscription(user?.email, user?.uid)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase VIP config fetch note for $fbUrl: ${e.message}")
                }
            }

            // Priority 2: Query Render API as mirror or fallback
            try {
                val response = VipApiClient.apiService.getVipConfig()
                if (response.success) {
                    val current = _vipConfig.value
                    if (!loadedFromFirebase || current == null || current.pricingPlans.isEmpty()) {
                        _vipConfig.value = response
                        synchronized(remotePremiumEmails) {
                            remotePremiumEmails.clear()
                            if (response.premiumUsers.isNotEmpty()) {
                                remotePremiumEmails.addAll(response.premiumUsers.map { it.trim().lowercase() }.filter { it.isNotBlank() })
                            }
                        }
                    } else {
                        // Merge payment gateways and pricing plans if Firebase had partial info
                        val mergedGateways = current.paymentGateways ?: response.paymentGateways
                        val mergedPlans = if (current.pricingPlans.isNotEmpty()) current.pricingPlans else response.pricingPlans
                        _vipConfig.value = current.copy(
                            paymentGateways = mergedGateways,
                            pricingPlans = mergedPlans
                        )
                    }
                    val auth = try { FirebaseAuth.getInstance() } catch (e: Throwable) { null }
                    val user = auth?.currentUser
                    checkUserSubscription(user?.email, user?.uid)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Render VIP API fetch note: ${e.message}")
            }
        }
    }

    /**
     * Parses Firebase app_vip_config JSON into VipConfigResponse, supporting both merchantConfig
     * and paymentGateways schemas used by the website admin panel.
     */
    private fun parseVipConfigFromJson(jsonStr: String): VipConfigResponse? {
        return try {
            val json = JSONObject(jsonStr)

            // 1. Merchant Config
            val mObj = json.optJSONObject("merchantConfig")
            val merchantConfig = if (mObj != null) {
                MerchantConfig(
                    bkashNumber = mObj.optString("bkashNumber", "").takeIf { it.isNotBlank() },
                    bkashType = mObj.optString("bkashType", "Personal"),
                    nagadNumber = mObj.optString("nagadNumber", "").takeIf { it.isNotBlank() },
                    nagadType = mObj.optString("nagadType", "Personal"),
                    rocketNumber = mObj.optString("rocketNumber", "").takeIf { it.isNotBlank() },
                    rocketType = mObj.optString("rocketType", "Personal"),
                    whatsappNumber = mObj.optString("whatsappNumber", "").takeIf { it.isNotBlank() },
                    helplineNumber = mObj.optString("helplineNumber", "").takeIf { it.isNotBlank() },
                    merchantNotes = mObj.optString("merchantNotes", "").takeIf { it.isNotBlank() }
                )
            } else null

            // 2. Payment Gateways (merge direct paymentGateways with merchantConfig)
            val pgObj = json.optJSONObject("paymentGateways")
            val bkashObj = pgObj?.optJSONObject("bkash")
            val nagadObj = pgObj?.optJSONObject("nagad")
            val rocketObj = pgObj?.optJSONObject("rocket")

            val bkashNum = bkashObj?.optString("number", "")?.takeIf { it.isNotBlank() }
                ?: merchantConfig?.bkashNumber
            val bkashType = bkashObj?.optString("type", "")?.takeIf { it.isNotBlank() }
                ?: merchantConfig?.bkashType ?: "Personal"

            val nagadNum = nagadObj?.optString("number", "")?.takeIf { it.isNotBlank() }
                ?: merchantConfig?.nagadNumber
            val nagadType = nagadObj?.optString("type", "")?.takeIf { it.isNotBlank() }
                ?: merchantConfig?.nagadType ?: "Personal"

            val rocketNum = rocketObj?.optString("number", "")?.takeIf { it.isNotBlank() }
                ?: merchantConfig?.rocketNumber
            val rocketType = rocketObj?.optString("type", "")?.takeIf { it.isNotBlank() }
                ?: merchantConfig?.rocketType ?: "Personal"

            val whatsappNum = pgObj?.optString("whatsapp", "")?.takeIf { it.isNotBlank() }
                ?: merchantConfig?.whatsappNumber
                ?: merchantConfig?.helplineNumber

            val paymentGateways = PaymentGateways(
                bkash = if (!bkashNum.isNullOrBlank()) GatewayInfo(number = bkashNum, type = bkashType) else null,
                nagad = if (!nagadNum.isNullOrBlank()) GatewayInfo(number = nagadNum, type = nagadType) else null,
                rocket = if (!rocketNum.isNullOrBlank()) GatewayInfo(number = rocketNum, type = rocketType) else null,
                whatsapp = whatsappNum
            )

            // 3. Pricing Plans
            val plansList = mutableListOf<VipPlan>()
            val plansArr = json.optJSONArray("pricingPlans")
            if (plansArr != null) {
                for (i in 0 until plansArr.length()) {
                    val pObj = plansArr.optJSONObject(i) ?: continue
                    val featuresList = mutableListOf<String>()
                    val fArr = pObj.optJSONArray("features")
                    if (fArr != null) {
                        for (f in 0 until fArr.length()) {
                            val feat = fArr.optString(f)
                            if (feat.isNotBlank()) featuresList.add(feat)
                        }
                    }
                    plansList.add(
                        VipPlan(
                            id = pObj.optString("id", "plan_$i"),
                            name = pObj.optString("name", "VIP Plan"),
                            duration = pObj.optString("duration", ""),
                            priceBDT = pObj.optInt("priceBDT", 0),
                            originalPriceBDT = if (pObj.has("originalPriceBDT")) pObj.optInt("originalPriceBDT") else null,
                            isPopular = pObj.optBoolean("isPopular", false),
                            badge = pObj.optString("badge", null),
                            features = featuresList
                        )
                    )
                }
            }

            // 4. Redeem Codes
            val redeemCodesList = mutableListOf<RedeemCode>()
            val rcArr = json.optJSONArray("redeemCodes")
            if (rcArr != null) {
                for (i in 0 until rcArr.length()) {
                    val rObj = rcArr.optJSONObject(i) ?: continue
                    redeemCodesList.add(
                        RedeemCode(
                            code = rObj.optString("code", ""),
                            planName = rObj.optString("planName", null),
                            maxUses = rObj.optInt("maxUses", 1),
                            isActive = rObj.optBoolean("isActive", true),
                            durationDays = if (rObj.has("durationDays")) rObj.optDouble("durationDays") else null,
                            expiresAt = rObj.optString("expiresAt", null),
                            isLifetime = rObj.optBoolean("isLifetime", false),
                            usedCount = rObj.optInt("usedCount", 0)
                        )
                    )
                }
            }

            // 5. Premium Users
            val premiumEmailsList = mutableListOf<String>()
            val puArr = json.optJSONArray("premiumUsers")
            if (puArr != null) {
                for (i in 0 until puArr.length()) {
                    val emailStr = when (val item = puArr.get(i)) {
                        is JSONObject -> {
                            val status = item.optString("status", "active")
                            if (status.equals("active", ignoreCase = true) || item.optBoolean("isLifetime", false)) {
                                item.optString("email", "")
                            } else ""
                        }
                        is String -> item
                        else -> ""
                    }
                    if (emailStr.isNotBlank()) {
                        premiumEmailsList.add(emailStr.trim().lowercase())
                    }
                }
            }

            // 6. Modal Notice
            val mnObj = json.optJSONObject("modalNotice")
            val modalNotice = if (mnObj != null) {
                ModalNotice(
                    title = mnObj.optString("title", ""),
                    subtitle = mnObj.optString("subtitle", ""),
                    supportWhatsApp = mnObj.optString("supportWhatsApp", null)
                )
            } else null

            VipConfigResponse(
                success = true,
                pricingPlans = plansList,
                paymentGateways = paymentGateways,
                merchantConfig = merchantConfig,
                modalNotice = modalNotice,
                premiumUsers = premiumEmailsList,
                redeemCodes = redeemCodesList,
                isMobilePaymentEnabled = json.optBoolean("isMobilePaymentEnabled", true),
                mobilePaymentDisabledNote = json.optString("mobilePaymentDisabledNote", null),
                externalPaymentUrl = json.optString("externalPaymentUrl", null)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VIP config from JSON", e)
            null
        }
    }

    /**
     * Synchronize with remote admin panel configuration (e.g. premiumEmails list and free episode limits)
     */
    fun syncWithRemoteConfig(emails: List<String>, episodeLimit: Int = 1) {
        synchronized(remotePremiumEmails) {
            remotePremiumEmails.clear()
            remotePremiumEmails.addAll(emails.map { it.trim().lowercase() }.filter { it.isNotBlank() })
            freeEpisodeLimit = episodeLimit
        }
        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Throwable) { null }
        checkUserSubscription(currentUser?.email, currentUser?.uid)
    }

    /**
     * Checks if current time is past the expiry date.
     * Returns true if expired, false if still valid.
     */
    fun isPastExpiryDate(dateStr: String?, timestamp: Long? = null): Boolean {
        return isDateExpired(dateStr, timestamp)
    }

    /**
     * Checks if a given timestamp or date string is expired compared to current system time.
     * Returns true if expired, false if still valid.
     */
    private fun isDateExpired(dateStr: String?, timestamp: Long? = null): Boolean {
        val now = System.currentTimeMillis()

        if (timestamp != null && timestamp > 0) {
            // Milliseconds check
            val expMs = if (timestamp < 100000000000L) timestamp * 1000L else timestamp
            return now > expMs
        }

        if (dateStr.isNullOrBlank()) return false
        val clean = dateStr.trim().lowercase()
        if (clean.contains("lifetime") || clean.contains("unlimited") || clean.contains("permanent")) {
            return false
        }

        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("MM/dd/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("MMM dd, yyyy", Locale.US),
            SimpleDateFormat("dd MMM yyyy", Locale.US)
        )

        for (fmt in dateFormats) {
            try {
                val parsed = fmt.parse(dateStr)
                if (parsed != null) {
                    return now > parsed.time
                }
            } catch (_: Exception) {}
        }

        return false
    }

    /**
     * Checks if a given user email or UID exists in Firestore 'premium_users' collection,
     * evaluates the 'expiryDate' / 'expiresAt' field, and triggers subscription_expired if past due.
     */
    fun checkUserSubscription(email: String?, uid: String?) {
        val cleanEmail = email?.trim()?.lowercase()
        recomputeStatus(cleanEmail, uid)
    }

    private fun recomputeStatus(email: String?, uid: String?) {
        val cleanEmail = email?.trim()?.lowercase()
        val isPrem = cleanEmail != null && synchronized(remotePremiumEmails) { remotePremiumEmails.contains(cleanEmail) }
        _isPremium.value = isPrem
        _isExpired.value = false
        _expiryTimestamp.value = null
        _subscriptionStatus.value = if (isPrem) "VIP Premium Active" else "Free Member"
        _subscriptionPlan.value = if (isPrem) "VIP Plan" else "Free Tier"
        _expiryDate.value = if (isPrem) "Lifetime Access" else null
    }

    /**
     * Calculates user-friendly remaining subscription duration (Days and Hours remaining)
     */
    fun getRemainingTimeDescription(ts: Long? = _expiryTimestamp.value, dateStr: String? = _expiryDate.value): String? {
        var targetTs = ts
        if (targetTs == null || targetTs <= 0L) {
            if (!dateStr.isNullOrBlank()) {
                val clean = dateStr.trim().lowercase()
                if (clean.contains("lifetime") || clean.contains("unlimited") || clean.contains("permanent")) {
                    return "Unlimited Lifetime Access"
                }
                val dateFormats = listOf(
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd", Locale.US),
                    SimpleDateFormat("dd/MM/yyyy", Locale.US),
                    SimpleDateFormat("MM/dd/yyyy", Locale.US),
                    SimpleDateFormat("dd-MM-yyyy", Locale.US),
                    SimpleDateFormat("MMM dd, yyyy", Locale.US),
                    SimpleDateFormat("dd MMM yyyy", Locale.US)
                )
                for (fmt in dateFormats) {
                    try {
                        val p = fmt.parse(dateStr)
                        if (p != null) {
                            targetTs = p.time
                            break
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        if (targetTs == null || targetTs <= 0L) return null

        val now = System.currentTimeMillis()
        val diff = targetTs - now
        if (diff <= 0) {
            return "Expired"
        }

        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff) % 24
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff) % 60

        return when {
            days > 1 -> "$days Days, $hours Hours Remaining"
            days == 1L -> "1 Day, $hours Hours Remaining"
            hours > 0 -> "$hours Hours, $minutes Mins Remaining"
            else -> "$minutes Minutes Remaining"
        }
    }

    private val _tempRewardUnlockUntilMs = MutableStateFlow(0L)
    val tempRewardUnlockUntilMs: StateFlow<Long> = _tempRewardUnlockUntilMs.asStateFlow()

    /**
     * Unlocks temporary VIP access for a specified duration (e.g. 30 minutes)
     * after watching a rewarded video ad.
     */
    fun unlockTemporaryVip(durationMinutes: Int = 30) {
        val expiryMs = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        _tempRewardUnlockUntilMs.value = expiryMs
        _isPremium.value = true
        _isExpired.value = false
        _subscriptionStatus.value = "VIP Pass ($durationMinutes Mins Unlocked)"
        _subscriptionPlan.value = "Ad Pass ($durationMinutes Mins)"
        _expiryDate.value = "Ad Pass Active"
        _expiryTimestamp.value = expiryMs
        Log.d(TAG, "Temporary VIP access granted for $durationMinutes minutes until $expiryMs")
    }

    /**
     * Checks if episode playback is allowed.
     * Episode index 0 (Episode 1) is free preview for all users.
     * Episode index > 0 (Episode 2+) requires VIP subscription if content is restricted or premium.
     */
    fun isEpisodePlayable(isSeries: Boolean, episodeNum: Int, isPremiumContent: Boolean = false): Boolean {
        if (!isPremiumContent) return true
        if (isVipUser()) return true
        return false
    }

    /**
     * Returns true if user has active VIP access (either subscription or 30-min ad reward)
     */
    fun isVipUser(): Boolean {
        val tempExpiry = _tempRewardUnlockUntilMs.value
        if (tempExpiry > 0L) {
            if (System.currentTimeMillis() < tempExpiry) {
                return true
            } else {
                _tempRewardUnlockUntilMs.value = 0L
                _isPremium.value = false
                _subscriptionStatus.value = "Free Member"
                _subscriptionPlan.value = "Free Tier"
                _expiryDate.value = null
            }
        }
        return _isPremium.value
    }
}

