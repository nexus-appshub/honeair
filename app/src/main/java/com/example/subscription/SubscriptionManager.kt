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
import org.json.JSONArray
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
    private val remotePremiumUsersMap = java.util.concurrent.ConcurrentHashMap<String, PremiumUserInfo>()
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
     * Live VIP config provider: Fetches configuration from Firebase RTDB and backend server.
     */
    fun fetchLiveVipConfig() {
        scope.launch(Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val endpoints = listOf(
                "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/app_vip_config.json",
                "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/configs/vipConfig.json",
                "https://homeair-backend.up.railway.app/api/vip/config",
                "${com.example.network.AppConfigManager.DEFAULT_BACKEND_URL}/api/vip/config"
            )

            var parsedConfig: VipConfigResponse? = null
            for (url in endpoints) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .header("Cache-Control", "no-cache")
                        .build()
                    client.newCall(req).execute().use { res ->
                        val body = res.body?.string()
                        if (res.isSuccessful && !body.isNullOrBlank() && body.trim() != "null" && body.trim().startsWith("{")) {
                            val config = parseVipConfigFromJson(body)
                            if (config != null && (config.pricingPlans.isNotEmpty() || config.redeemCodes.isNotEmpty() || config.merchantConfig != null)) {
                                parsedConfig = config
                                return@use
                            }
                        }
                    }
                    if (parsedConfig != null) break
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching VIP config from $url: ${e.message}")
                }
            }

            if (parsedConfig != null) {
                _vipConfig.value = parsedConfig
            } else if (_vipConfig.value == null) {
                _vipConfig.value = VipConfigResponse(
                    success = true,
                    version = "2.0",
                    pricingPlans = emptyList(),
                    paymentGateways = null,
                    merchantConfig = null,
                    premiumUsers = emptyList()
                )
            }

            val auth = try { FirebaseAuth.getInstance() } catch (e: Throwable) { null }
            val user = auth?.currentUser
            checkUserSubscription(user?.email, user?.uid)
        }
    }

    /**
     * Grants an active VIP status directly in memory upon successful code redemption
     */
    fun grantUserRedeemPass(
        email: String,
        planName: String,
        expiryDateStr: String?,
        expiryTimestamp: Long?,
        isLifetime: Boolean
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return

        val info = PremiumUserInfo(
            email = cleanEmail,
            isLifetime = isLifetime,
            planName = planName,
            expiryDate = expiryDateStr,
            expiryTimestamp = expiryTimestamp,
            isExpired = false
        )
        remotePremiumUsersMap[cleanEmail] = info
        synchronized(remotePremiumEmails) {
            remotePremiumEmails.add(cleanEmail)
        }
        recomputeStatus(cleanEmail, null)
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

            // 3. Pricing Plans (Supports Array or Map Object)
            val plansList = mutableListOf<VipPlan>()
            val rawPlans = json.opt("pricingPlans")
            when (rawPlans) {
                is JSONArray -> {
                    for (i in 0 until rawPlans.length()) {
                        val pObj = rawPlans.optJSONObject(i) ?: continue
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
                                priceBDT = pObj.optInt("priceBDT", pObj.optInt("price", 0)),
                                originalPriceBDT = if (pObj.has("originalPriceBDT")) pObj.optInt("originalPriceBDT") else null,
                                isPopular = pObj.optBoolean("isPopular", false),
                                badge = pObj.optString("badge", "").takeIf { it.isNotBlank() },
                                features = featuresList
                            )
                        )
                    }
                }
                is JSONObject -> {
                    val keys = rawPlans.keys()
                    var idx = 0
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val pObj = rawPlans.optJSONObject(k) ?: continue
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
                                id = pObj.optString("id", k),
                                name = pObj.optString("name", "VIP Plan"),
                                duration = pObj.optString("duration", ""),
                                priceBDT = pObj.optInt("priceBDT", pObj.optInt("price", 0)),
                                originalPriceBDT = if (pObj.has("originalPriceBDT")) pObj.optInt("originalPriceBDT") else null,
                                isPopular = pObj.optBoolean("isPopular", false),
                                badge = pObj.optString("badge", "").takeIf { it.isNotBlank() },
                                features = featuresList
                            )
                        )
                        idx++
                    }
                }
            }

            // 4. Redeem Codes (Supports Array or Map Object)
            val redeemCodesList = mutableListOf<RedeemCode>()
            val rawCodes = json.opt("redeemCodes")
            when (rawCodes) {
                is JSONArray -> {
                    for (i in 0 until rawCodes.length()) {
                        val rObj = rawCodes.optJSONObject(i) ?: continue
                        val codeStr = rObj.optString("code", "").trim()
                        if (codeStr.isNotBlank()) {
                            val durDays: Double? = if (rObj.has("durationDays")) rObj.optDouble("durationDays") else null
                            redeemCodesList.add(
                                RedeemCode(
                                    code = codeStr,
                                    planName = rObj.optString("planName", "VIP Plan"),
                                    maxUses = rObj.optInt("maxUses", 1),
                                    isActive = rObj.optBoolean("isActive", true),
                                    durationDays = durDays,
                                    expiresAt = rObj.optString("expiresAt", "").takeIf { it.isNotBlank() },
                                    isLifetime = rObj.optBoolean("isLifetime", false),
                                    usedCount = rObj.optInt("usedCount", 0)
                                )
                            )
                        }
                    }
                }
                is JSONObject -> {
                    val keys = rawCodes.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val rObj = rawCodes.optJSONObject(k) ?: continue
                        val codeStr = rObj.optString("code", k).trim()
                        if (codeStr.isNotBlank()) {
                            val durDays: Double? = if (rObj.has("durationDays")) rObj.optDouble("durationDays") else null
                            redeemCodesList.add(
                                RedeemCode(
                                    code = codeStr,
                                    planName = rObj.optString("planName", "VIP Plan"),
                                    maxUses = rObj.optInt("maxUses", 1),
                                    isActive = rObj.optBoolean("isActive", true),
                                    durationDays = durDays,
                                    expiresAt = rObj.optString("expiresAt", "").takeIf { it.isNotBlank() },
                                    isLifetime = rObj.optBoolean("isLifetime", false),
                                    usedCount = rObj.optInt("usedCount", 0)
                                )
                            )
                        }
                    }
                }
            }

            // 5. Premium Users (Supports Array or Map Object)
            val premiumEmailsList = mutableListOf<String>()
            val rawPu = json.opt("premiumUsers")
            when (rawPu) {
                is JSONArray -> {
                    for (i in 0 until rawPu.length()) {
                        when (val item = rawPu.get(i)) {
                            is JSONObject -> {
                                val email = item.optString("email", "").trim().lowercase()
                                if (email.isNotBlank()) {
                                    val status = item.optString("status", "active")
                                    val isLifetime = item.optBoolean("isLifetime", false)
                                    val plan = item.optString("planName", item.optString("plan", "VIP Plan"))
                                    val expDate = item.optString("expiresAt", item.optString("expiryDate", item.optString("validUntil", ""))).ifBlank { null }
                                    val expTs = if (item.has("expiresAtTimestamp")) item.optLong("expiresAtTimestamp") else null

                                    val expired = if (isLifetime) {
                                        false
                                    } else if (status.equals("expired", ignoreCase = true)) {
                                        true
                                    } else {
                                        isDateExpired(expDate, expTs)
                                    }

                                    val userInfo = PremiumUserInfo(
                                        email = email,
                                        isLifetime = isLifetime,
                                        planName = plan,
                                        expiryDate = expDate,
                                        expiryTimestamp = expTs,
                                        isExpired = expired
                                    )
                                    remotePremiumUsersMap[email] = userInfo
                                    if (!expired) {
                                        premiumEmailsList.add(email)
                                    }
                                }
                            }
                            is String -> {
                                val email = item.trim().lowercase()
                                if (email.isNotBlank()) {
                                    remotePremiumUsersMap[email] = PremiumUserInfo(email = email, isLifetime = false, planName = "VIP Plan")
                                    premiumEmailsList.add(email)
                                }
                            }
                        }
                    }
                }
                is JSONObject -> {
                    val keys = rawPu.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val item = rawPu.optJSONObject(k)
                        if (item != null) {
                            val email = item.optString("email", k).trim().lowercase()
                            if (email.isNotBlank()) {
                                val status = item.optString("status", "active")
                                val isLifetime = item.optBoolean("isLifetime", false)
                                val plan = item.optString("planName", item.optString("plan", "VIP Plan"))
                                val expDate = item.optString("expiresAt", item.optString("expiryDate", item.optString("validUntil", ""))).ifBlank { null }
                                val expTs = if (item.has("expiresAtTimestamp")) item.optLong("expiresAtTimestamp") else null

                                val expired = if (isLifetime) {
                                    false
                                } else if (status.equals("expired", ignoreCase = true)) {
                                    true
                                } else {
                                    isDateExpired(expDate, expTs)
                                }

                                val userInfo = PremiumUserInfo(
                                    email = email,
                                    isLifetime = isLifetime,
                                    planName = plan,
                                    expiryDate = expDate,
                                    expiryTimestamp = expTs,
                                    isExpired = expired
                                )
                                remotePremiumUsersMap[email] = userInfo
                                if (!expired) {
                                    premiumEmailsList.add(email)
                                }
                            }
                        }
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
        if (cleanEmail.isNullOrBlank()) {
            _isPremium.value = false
            _isExpired.value = false
            _expiryTimestamp.value = null
            _subscriptionStatus.value = "Free Member"
            _subscriptionPlan.value = "Free Tier"
            _expiryDate.value = null
            return
        }

        val userInfo = remotePremiumUsersMap[cleanEmail]
        val inRemoteList = synchronized(remotePremiumEmails) { remotePremiumEmails.contains(cleanEmail) }

        if (userInfo != null) {
            if (userInfo.isLifetime) {
                _isPremium.value = true
                _isExpired.value = false
                _expiryTimestamp.value = null
                _subscriptionStatus.value = "VIP Premium Active"
                _subscriptionPlan.value = "Lifetime VIP"
                _expiryDate.value = "Lifetime Access"
            } else if (userInfo.isExpired || isDateExpired(userInfo.expiryDate, userInfo.expiryTimestamp)) {
                // Expired! Revert to Free Tier!
                _isPremium.value = false
                _isExpired.value = true
                _expiryTimestamp.value = userInfo.expiryTimestamp
                _subscriptionStatus.value = "Subscription Expired"
                _subscriptionPlan.value = "Free Tier"
                _expiryDate.value = userInfo.expiryDate ?: "Expired"
            } else if (inRemoteList) {
                // Active VIP plan with valid expiry date
                _isPremium.value = true
                _isExpired.value = false
                _expiryTimestamp.value = userInfo.expiryTimestamp
                _subscriptionStatus.value = "VIP Premium Active"
                _subscriptionPlan.value = userInfo.planName
                _expiryDate.value = userInfo.expiryDate ?: "Active Subscription"
            } else {
                _isPremium.value = false
                _isExpired.value = false
                _expiryTimestamp.value = null
                _subscriptionStatus.value = "Free Member"
                _subscriptionPlan.value = "Free Tier"
                _expiryDate.value = null
            }
        } else if (inRemoteList) {
            // Found in remote email list without explicit metadata - DO NOT assume lifetime!
            _isPremium.value = true
            _isExpired.value = false
            _expiryTimestamp.value = null
            _subscriptionStatus.value = "VIP Premium Active"
            _subscriptionPlan.value = "VIP Plan"
            _expiryDate.value = "Active Subscription"
        } else {
            // Free Tier
            _isPremium.value = false
            _isExpired.value = false
            _expiryTimestamp.value = null
            _subscriptionStatus.value = "Free Member"
            _subscriptionPlan.value = "Free Tier"
            _expiryDate.value = null
        }
    }

    fun isLifetimeUser(email: String?): Boolean {
        val clean = email?.trim()?.lowercase() ?: return false
        return remotePremiumUsersMap[clean]?.isLifetime == true
    }

    fun hasValidPaidSubscription(email: String?): Boolean {
        val clean = email?.trim()?.lowercase() ?: return false
        val user = remotePremiumUsersMap[clean] ?: return false
        return user.isLifetime || (!user.isExpired && !isDateExpired(user.expiryDate, user.expiryTimestamp))
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

    /**
     * Checks if episode playback is allowed.
     * Episode index 0 (Episode 1) is free preview for all users.
     * Episode index > 0 (Episode 2+) requires VIP subscription if content is restricted or premium.
     */
    fun isEpisodePlayable(isSeries: Boolean, episodeNum: Int, isPremiumContent: Boolean = false): Boolean {
        if (!isPremiumContent) return true
        if (_isPremium.value) return true
        return false
    }

    /**
     * Returns true if user has active VIP access
     */
    fun isVipUser(): Boolean {
        return _isPremium.value && !_isExpired.value
    }
}

data class PremiumUserInfo(
    val email: String,
    val isLifetime: Boolean = false,
    val planName: String = "VIP Plan",
    val expiryDate: String? = null,
    val expiryTimestamp: Long? = null,
    val isExpired: Boolean = false
)

