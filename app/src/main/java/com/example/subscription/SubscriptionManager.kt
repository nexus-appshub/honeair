package com.example.subscription

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SubscriptionManager manages VIP / Premium membership status using Firebase Auth & Firestore,
 * with full date-based expiry checks (7 days, 30 days, 1 year, Lifetime) and fallback support.
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

    private val _isExpired = MutableStateFlow(false)
    val isExpired: StateFlow<Boolean> = _isExpired.asStateFlow()

    private val remotePremiumEmails = mutableSetOf<String>()
    private var freeEpisodeLimit: Int = 1

    init {
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
     * Checks if a given user email or UID exists in Firestore 'premium_users' collection or remote whitelist
     */
    fun checkUserSubscription(email: String?, uid: String?) {
        val cleanEmail = email?.trim()?.lowercase()
        val cleanUid = uid?.trim()

        // 1. Fast check against whitelist / admin email list
        if (cleanEmail != null && synchronized(remotePremiumEmails) { remotePremiumEmails.contains(cleanEmail) }) {
            _isPremium.value = true
            _isExpired.value = false
            _subscriptionStatus.value = "VIP Premium Active"
            _subscriptionPlan.value = "VIP Lifetime Plan"
            _expiryDate.value = "Lifetime Access"
            return
        }

        // 2. Query Firestore 'premium_users' collection
        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                var foundPremium = false
                var planTitle = "VIP Premium"
                var validUntil: String? = null
                var hasExpiredFlag = false

                // Helper to parse document fields
                fun inspectDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Boolean {
                    if (!doc.exists()) return false
                    val active = doc.getBoolean("isPremium") ?: doc.getBoolean("active") ?: true
                    planTitle = doc.getString("plan") ?: doc.getString("planName") ?: "VIP Premium Plan"
                    
                    // Duration / Expiry dates: can be string or timestamp
                    validUntil = doc.getString("expiresAt") 
                        ?: doc.getString("validUntil") 
                        ?: doc.getString("expiryDate")
                        ?: doc.getString("endDate")

                    val expiresTimestamp = doc.getLong("expiresAtTimestamp") 
                        ?: doc.getLong("expiryTimestamp")
                        ?: doc.getTimestamp("expiresAt")?.toDate()?.time
                        ?: doc.getTimestamp("validUntil")?.toDate()?.time

                    val expired = isDateExpired(validUntil, expiresTimestamp)
                    if (expired) {
                        hasExpiredFlag = true
                        return false // Subscription has expired
                    }

                    return active
                }

                // A. Check by UID document ID
                if (!cleanUid.isNullOrBlank()) {
                    try {
                        val doc = db.collection(COLLECTION_PREMIUM_USERS).document(cleanUid).get().await()
                        if (doc != null && inspectDoc(doc)) {
                            foundPremium = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Firestore check by UID error: ${e.message}")
                    }
                }

                // B. Check by email query if not found by UID
                if (!foundPremium && !cleanEmail.isNullOrBlank()) {
                    try {
                        val querySnapshot = db.collection(COLLECTION_PREMIUM_USERS)
                            .whereEqualTo("email", cleanEmail)
                            .get()
                            .await()

                        if (!querySnapshot.isEmpty) {
                            val doc = querySnapshot.documents.firstOrNull()
                            if (doc != null && inspectDoc(doc)) {
                                foundPremium = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Firestore check by email error: ${e.message}")
                    }
                }

                // Fallback check against remote config again
                if (!foundPremium && cleanEmail != null && !hasExpiredFlag) {
                    foundPremium = synchronized(remotePremiumEmails) { remotePremiumEmails.contains(cleanEmail) }
                }

                _isPremium.value = foundPremium
                _isExpired.value = hasExpiredFlag && !foundPremium
                _subscriptionStatus.value = when {
                    foundPremium -> "VIP Premium Active"
                    hasExpiredFlag -> "Subscription Expired"
                    else -> "Free Member"
                }
                _subscriptionPlan.value = if (foundPremium || hasExpiredFlag) planTitle else "Free Tier"
                _expiryDate.value = when {
                    foundPremium -> validUntil?.let { "Valid until $it" } ?: "Active VIP"
                    hasExpiredFlag -> validUntil?.let { "Expired on $it" } ?: "Expired"
                    else -> null
                }

            } catch (e: Throwable) {
                Log.w(TAG, "General Firestore subscription lookup error: ${e.message}")
                recomputeStatus(cleanEmail, cleanUid)
            }
        }
    }

    private fun recomputeStatus(email: String?, uid: String?) {
        val cleanEmail = email?.trim()?.lowercase()
        val isPrem = cleanEmail != null && synchronized(remotePremiumEmails) { remotePremiumEmails.contains(cleanEmail) }
        _isPremium.value = isPrem
        _isExpired.value = false
        _subscriptionStatus.value = if (isPrem) "VIP Premium Active" else "Free Member"
        _subscriptionPlan.value = if (isPrem) "VIP Plan" else "Free Tier"
        _expiryDate.value = if (isPrem) "Lifetime Access" else null
    }

    /**
     * Checks if episode playback is allowed.
     * Episode index 0 (Episode 1) is free preview for all users.
     * Episode index > 0 (Episode 2+) requires VIP subscription if content is restricted or premium.
     */
    fun isEpisodePlayable(isSeries: Boolean, episodeNum: Int, isPremiumContent: Boolean = false): Boolean {
        if (!isSeries && !isPremiumContent) return true
        if (_isPremium.value) return true

        // For series: Episode 1 is free to preview (episode index == 0 or episodeNum <= freeEpisodeLimit)
        if (isSeries && episodeNum <= freeEpisodeLimit) {
            return true
        }

        // Non-premium user attempting to watch Episode 2+ of a series or locked content
        return false
    }

    /**
     * Returns true if user has active VIP access
     */
    fun isVipUser(): Boolean {
        return _isPremium.value
    }
}
