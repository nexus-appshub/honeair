package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AdminLogEntity
import com.example.data.database.AppDatabase
import com.example.data.model.IptvChannel
import com.example.data.model.IptvPlaylist
import com.example.data.model.MediaItem
import com.example.data.model.FloatingPlayerInstance
import com.example.data.repository.MediaRepository
import com.example.data.repository.StreamRepository
import com.example.data.repository.HomaiRepository
import com.example.data.repository.HomaiMessage
import com.example.update.AppUpdateManager
import com.example.update.AppUpdateWorker
import com.example.update.UpdateInfo
import com.example.update.UpdateCheckResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class UserProfile(
    val name: String,
    val email: String,
    val avatarUrl: String,
    val isSuperAdmin: Boolean,
    val userId: String = ""
)

data class TelemetryStats(
    val liveUsers: Int = 104523,
    val visitedRealUsers: Int = 412095,
    val totalUsers: Int = 890532,
    val serverHealth: String = "HEALTHY", // "HEALTHY", "WARNING", "CRITICAL"
    val cdnResponseMs: Int = 42,
    val cpuUsage: Int = 24,
    val memoryUsage: Int = 48,
    val activeBandwidthGbps: Double = 312.4,
    val unauthorizedAccessAttempts: Int = 0
)

data class AppNotice(
    val title: String = "",
    val message: String = "",
    val imageUrl: String? = null,
    val buttonText: String? = null,
    val buttonUrl: String? = null,
    val isDismissible: Boolean = true
)

data class AppControlConfig(
    val isAppSuspended: Boolean = false,
    val suspensionTitle: String = "App Under Maintenance",
    val suspensionMessage: String = "App access is temporarily suspended by administrator. Please check back later.",
    val notice: AppNotice? = null,
    val isSportsTabLocked: Boolean = false,
    val sportsTabStatusText: String = "Live",
    val sportsLockReason: String = "Sports hub is currently locked by administrator.",
    val fancodeCode: String = "",
    val isFanCodeLocked: Boolean = false,
    val lockedTabs: List<String> = emptyList(),
    val premiumCategories: List<String> = emptyList(),
    val premiumMediaIds: List<String> = emptyList(),
    val premiumEmails: List<String> = emptyList(),
    val freeEpisodeLimit: Int = 1,
    val isPremiumRequired: Boolean = false,
    val premiumPaywallTitle: String = "VIP Premium Subscription Required",
    val premiumPaywallMessage: String = "This content or tab is reserved for Premium Subscribers. Please purchase a subscription to continue.",
    val premiumPaywallButtonText: String = "Buy Subscription Now",
    val premiumPaywallButtonUrl: String = "",
    val isAdsEnabled: Boolean = false,
    val adBannerUrl: String = "",
    val adClickUrl: String = "",
    val adTitle: String = "Sponsored: Upgrade to VIP to Remove Ads",
    val redeemCode: String = "",
    val redeemValidityHours: Int = 24,
    val redeemExpiryTimestamp: Long = 0L,
    val premiumLiveTvIds: List<String> = emptyList(),
    val premiumLiveTvCategories: List<String> = emptyList(),
    val isLiveTvLockEnabled: Boolean = false
)

class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var repository: StreamRepository
    private val mediaRepository = MediaRepository()

    // Auth State
    private val _tabReselectEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val tabReselectEvent: SharedFlow<Int> = _tabReselectEvent.asSharedFlow()

    fun triggerTabReselect(tabIndex: Int) {
        _tabReselectEvent.tryEmit(tabIndex)
    }

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val registeredUserDao = AppDatabase.getDatabase(application).registeredUserDao()
    val registeredUsers: StateFlow<List<com.example.data.database.RegisteredUserEntity>> = registeredUserDao.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val premiumMediaDao = AppDatabase.getDatabase(application).premiumMediaDao()
    val premiumMedia: StateFlow<List<com.example.data.database.PremiumMediaEntity>> = premiumMediaDao.getAllPremiumMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePremiumMedia(id: String) {
        viewModelScope.launch {
            val isPremium = premiumMediaDao.isPremiumMedia(id).first()
            if (isPremium) {
                premiumMediaDao.deletePremiumMedia(id)
            } else {
                premiumMediaDao.insertPremiumMedia(com.example.data.database.PremiumMediaEntity(id))
            }
        }
    }

    fun toggleUserRestriction(email: String, restrict: Boolean) {
        viewModelScope.launch {
            registeredUserDao.updateRestriction(email, restrict)
        }
    }

    fun toggleUserBan(email: String, ban: Boolean) {
        viewModelScope.launch {
            registeredUserDao.updateBanStatus(email, ban)
        }
    }

    private val _appControlConfig = MutableStateFlow<AppControlConfig?>(
        if (application.getSharedPreferences("app_remote_control", Context.MODE_PRIVATE).contains("isAppSuspended")) {
            val p = application.getSharedPreferences("app_remote_control", Context.MODE_PRIVATE)
            AppControlConfig(
                isAppSuspended = p.getBoolean("isAppSuspended", false),
                suspensionTitle = p.getString("suspensionTitle", "App Under Maintenance") ?: "App Under Maintenance",
                suspensionMessage = p.getString("suspensionMessage", "App access is temporarily suspended by administrator. Please check back later.") ?: "App access is temporarily suspended by administrator. Please check back later.",
                isSportsTabLocked = p.getBoolean("isSportsTabLocked", false),
                sportsTabStatusText = p.getString("sportsTabStatusText", "Live") ?: "Live",
                sportsLockReason = p.getString("sportsLockReason", "Sports hub is currently locked by administrator.") ?: "Sports hub is currently locked by administrator.",
                fancodeCode = p.getString("fancodeCode", "") ?: "",
                isFanCodeLocked = p.getBoolean("isFanCodeLocked", false),
                redeemCode = p.getString("redeemCode", "") ?: "",
                redeemValidityHours = p.getInt("redeemValidityHours", 24),
                redeemExpiryTimestamp = p.getLong("redeemExpiryTimestamp", 0L)
            )
        } else null
    )
    val appControlConfig: StateFlow<AppControlConfig?> = _appControlConfig.asStateFlow()

    // Media Hub State (Movies, Anime, K-Dramas, TV Shows, Short TV, Hindi Dubbed)
    private val _mediaState = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
    val mediaState: StateFlow<UiState<List<MediaItem>>> = combine(_mediaState, premiumMedia, _appControlConfig) { state, premiumList, config ->
        when (state) {
            is UiState.Success -> {
                val premiumIdsFromDb = premiumList.map { it.id }.toSet()
                val configPremiumIds = config?.premiumMediaIds ?: emptyList()
                val configPremiumCats = config?.premiumCategories ?: emptyList()
                val configLockedTabs = config?.lockedTabs ?: emptyList()
                
                val mappedData = state.data.map { item ->
                    val cleanId = item.id.trim()
                    val cleanTitle = item.title.trim()
                    val cleanCategory = item.category.trim().lowercase()
                    
                    val isPrem = item.isPremium || 
                                 premiumIdsFromDb.contains(cleanId) ||
                                 (cleanId.isNotBlank() && configPremiumIds.contains(cleanId)) ||
                                 (cleanTitle.isNotBlank() && configPremiumIds.any { cleanTitle.contains(it, ignoreCase = true) }) ||
                                 (cleanCategory.isNotBlank() && (configPremiumCats.contains(cleanCategory) || configLockedTabs.contains(cleanCategory)))
                                 
                    if (isPrem) {
                        item.copy(isPremium = true)
                    } else {
                        item
                    }
                }
                UiState.Success(mappedData)
            }
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private val loadedCategories = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
        add("All")
        add("Latest")
        add("Movies")
        add("Series & TV Shows")
        add("Bangla Cinema & Natok")
        add("Anime Series")
        add("Anime Movies")
    }

    private var backgroundPreloadJob: kotlinx.coroutines.Job? = null
    private var isLoadingMore = false
    private var currentPage = 2

    private val _selectedMediaCategory = MutableStateFlow("All")
    val selectedMediaCategory: StateFlow<String> = _selectedMediaCategory.asStateFlow()

    private val _showCategoriesGrid = MutableStateFlow(false)
    val showCategoriesGrid: StateFlow<Boolean> = _showCategoriesGrid.asStateFlow()

    fun setShowCategoriesGrid(show: Boolean) {
        _showCategoriesGrid.value = show
    }

    private val _mediaSearchQuery = MutableStateFlow("")
    val mediaSearchQuery: StateFlow<String> = _mediaSearchQuery.asStateFlow()

    val latestReleases: StateFlow<List<MediaItem>> = mediaState.map { state ->
        if (state is UiState.Success) {
            val list = state.data
            
            // 1. Get items explicitly marked as Latest/Latest Releases
            val explicitLatest = list.filter { item ->
                item.category.contains("Latest", ignoreCase = true)
            }
            
            // 2. Get 2025/2026 items from other categories
            val otherRecent = list.filter { item ->
                !item.category.contains("Latest", ignoreCase = true) &&
                (item.year.toIntOrNull() ?: 0) >= 2025
            }
            
            // Combine and distinct
            val combined = (explicitLatest + otherRecent).distinctBy { it.id }
            
            // Separate into groups to ensure a highly balanced representation
            // Split into Movies, TV Shows, Anime
            val movies = combined.filter { it.type == "movie" }
                .sortedWith(compareByDescending<MediaItem> { it.year.toIntOrNull() ?: 0 }.thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 })
            val series = combined.filter { (it.type == "series" || it.type == "tv") && !it.category.contains("Anime", ignoreCase = true) }
                .sortedWith(compareByDescending<MediaItem> { it.year.toIntOrNull() ?: 0 }.thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 })
            val anime = combined.filter { it.category.contains("Anime", ignoreCase = true) }
                .sortedWith(compareByDescending<MediaItem> { it.year.toIntOrNull() ?: 0 }.thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 })
            
            // Re-mix them proportionally (Movie, Series, Anime, Movie, Series, Anime)
            val mixedList = mutableListOf<MediaItem>()
            val maxCount = maxOf(movies.size, series.size, anime.size)
            for (i in 0 until maxCount) {
                if (i < movies.size) mixedList.add(movies[i])
                if (i < series.size) mixedList.add(series[i])
                if (i < anime.size) mixedList.add(anime[i])
            }
            
            mixedList.toList()
        } else {
            emptyList()
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playlist index
    private val _playlistsState = MutableStateFlow<UiState<List<IptvPlaylist>>>(UiState.Loading)
    val playlistsState: StateFlow<UiState<List<IptvPlaylist>>> = _playlistsState.asStateFlow()

    private val _playlistSearchQuery = MutableStateFlow("")
    val playlistSearchQuery: StateFlow<String> = _playlistSearchQuery.asStateFlow()

    // Full Screen Player State
    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    fun setFullScreen(fullScreen: Boolean) {
        _isFullScreen.value = fullScreen
    }

    // Global Download Library visibility state
    private val _showDownloadLibraryGlobal = MutableStateFlow(false)
    val showDownloadLibraryGlobal: StateFlow<Boolean> = _showDownloadLibraryGlobal.asStateFlow()

    fun setShowDownloadLibraryGlobal(show: Boolean) {
        _showDownloadLibraryGlobal.value = show
    }

    // Channels state
    private val _selectedPlaylist = MutableStateFlow<IptvPlaylist?>(null)
    val selectedPlaylist: StateFlow<IptvPlaylist?> = _selectedPlaylist.asStateFlow()

    private val _channelsState = MutableStateFlow<UiState<List<IptvChannel>>>(UiState.Success(emptyList()))
    val channelsState: StateFlow<UiState<List<IptvChannel>>> = _channelsState.asStateFlow()

    private val _channelSearchQuery = MutableStateFlow("")
    val channelSearchQuery: StateFlow<String> = _channelSearchQuery.asStateFlow()

    private val _selectedChannelGroup = MutableStateFlow("All")
    val selectedChannelGroup: StateFlow<String> = _selectedChannelGroup.asStateFlow()

    fun setSelectedChannelGroup(group: String) {
        _selectedChannelGroup.value = group
    }

    val availableChannelGroups: StateFlow<List<String>> = _channelsState.map { state ->
        if (state is UiState.Success) {
            val groups = state.data.map { it.group.ifBlank { "General" } }.filter { it.isNotBlank() }.distinct()
            listOf("All") + groups
        } else {
            listOf("All")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    // Playback state
    private val _activeChannel = MutableStateFlow<IptvChannel?>(null)
    val activeChannel: StateFlow<IptvChannel?> = _activeChannel.asStateFlow()

    private val _playbackErrorChannelUrl = MutableStateFlow<String?>(null)
    val playbackErrorChannelUrl: StateFlow<String?> = _playbackErrorChannelUrl.asStateFlow()

    private val _useCircularChannelsLayout = MutableStateFlow(true)
    val useCircularChannelsLayout: StateFlow<Boolean> = _useCircularChannelsLayout.asStateFlow()

    fun toggleChannelsLayout() {
        _useCircularChannelsLayout.value = !_useCircularChannelsLayout.value
    }

    // Sports & Remote App Control state variables
    private val controlPrefs by lazy {
        application.getSharedPreferences("app_remote_control", Context.MODE_PRIVATE)
    }

    private val _isCheckingSuspension = MutableStateFlow(false)
    val isCheckingSuspension: StateFlow<Boolean> = _isCheckingSuspension.asStateFlow()

    private val _isInitialControlChecked = MutableStateFlow(false)
    val isInitialControlChecked: StateFlow<Boolean> = _isInitialControlChecked.asStateFlow()

    private val _isSportsUnlockedLocally = MutableStateFlow(false)
    val isSportsUnlockedLocally: StateFlow<Boolean> = _isSportsUnlockedLocally.asStateFlow()

    private val _isAppUnlockedWithFanCode = MutableStateFlow(false)
    val isAppUnlockedWithFanCode: StateFlow<Boolean> = _isAppUnlockedWithFanCode.asStateFlow()

    private val _showPremiumPaywall = MutableStateFlow(false)
    val showPremiumPaywall: StateFlow<Boolean> = _showPremiumPaywall.asStateFlow()

    fun triggerPremiumPaywall(show: Boolean = true) {
        _showPremiumPaywall.value = show
    }

    fun getRedeemUnlockExpiry(): Long {
        return sharedPrefs.getLong("redeem_unlocked_until", 0L)
    }

    fun isRedeemCodeActive(userEmail: String?): Boolean {
        val cleanEmail = userEmail?.trim()?.lowercase() ?: ""
        if (cleanEmail.isBlank()) return false
        
        val unlockedUntil = sharedPrefs.getLong("redeem_unlocked_until", 0L)
        val unlockedUser = sharedPrefs.getString("redeem_unlocked_user", "") ?: ""
        
        if (unlockedUser.trim().lowercase() != cleanEmail) {
            return false
        }
        
        return System.currentTimeMillis() < unlockedUntil
    }

    suspend fun applyRedeemCode(code: String, userEmail: String?): Pair<Boolean, String> {
        val cleanEmail = userEmail?.trim()?.lowercase() ?: ""
        if (cleanEmail.isBlank()) {
            return Pair(false, "Please login first.")
        }

        val entered = code.trim()
        if (entered.isBlank()) {
            return Pair(false, "Invalid code! Please check and try again.")
        }

        return try {
            val response = com.example.data.api.VipApiClient.apiService.redeemCode(
                com.example.data.api.RedeemRequest(
                    code = entered,
                    email = cleanEmail,
                    platform = "android"
                )
            )
            
            if (response.success) {
                // If successful, refresh the VIP config so the local app knows they are premium
                com.example.subscription.SubscriptionManager.fetchLiveVipConfig()

                // Save locally to display the "Time Left" duration in the profile tab
                val config = _appControlConfig.value
                val expiryTimestamp = config?.redeemExpiryTimestamp ?: 0L
                val validityHours = config?.redeemValidityHours ?: 24
                val validityMs = validityHours * 60 * 60 * 1000L
                var unlockUntil = System.currentTimeMillis() + validityMs
                if (expiryTimestamp > 0L) {
                    unlockUntil = unlockUntil.coerceAtMost(expiryTimestamp)
                }
                
                sharedPrefs.edit()
                    .putLong("redeem_unlocked_until", unlockUntil)
                    .putString("redeem_unlocked_user", cleanEmail)
                    .putString("redeem_unlocked_code", entered)
                    .putString("redeem_plan_name", response.planName ?: "VIP Promo Pass")
                    .apply()

                Pair(true, response.message ?: "Congratulations! VIP has been activated.")
            } else {
                Pair(false, response.message ?: "Invalid or expired code.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Network error while applying redeem code. Please try again.")
        }
    }

    fun getRedeemPlanName(): String {
        return sharedPrefs.getString("redeem_plan_name", "VIP Promo Pass") ?: "VIP Promo Pass"
    }

    fun isUserPremium(userEmail: String?): Boolean {
        if (com.example.subscription.SubscriptionManager.isVipUser()) return true
        if (isRedeemCodeActive(userEmail)) return true
        val config = _appControlConfig.value
        val cleanEmail = userEmail?.trim()?.lowercase() ?: ""
        if (cleanEmail.isBlank()) return false
        if (cleanEmail.contains("admin") || cleanEmail == "xubilas.era@gmail.com") return true
        return config?.premiumEmails?.any { it.trim().equals(cleanEmail, ignoreCase = true) } == true
    }

    fun checkContentAccess(
        mediaId: String?,
        title: String?,
        category: String?,
        episodeNum: Int = 1,
        userEmail: String?
    ): Boolean {
        if (isUserPremium(userEmail)) return true

        val config = _appControlConfig.value ?: return true

        val cleanMediaId = mediaId?.trim() ?: ""
        val cleanTitle = title?.trim() ?: ""
        val cleanCategory = category?.trim()?.lowercase() ?: ""

        val isMediaLocked = (cleanMediaId.isNotBlank() && config.premiumMediaIds.contains(cleanMediaId)) ||
                (cleanTitle.isNotBlank() && config.premiumMediaIds.any { cleanTitle.contains(it, ignoreCase = true) })

        val isCategoryLocked = cleanCategory.isNotBlank() && 
                (config.premiumCategories.contains(cleanCategory) || config.lockedTabs.contains(cleanCategory))

        if (episodeNum > config.freeEpisodeLimit) {
            if (isMediaLocked || isCategoryLocked || config.isPremiumRequired) {
                triggerPremiumPaywall(true)
                return false
            }
        }

        if (isMediaLocked || (isCategoryLocked && config.isPremiumRequired)) {
            triggerPremiumPaywall(true)
            return false
        }

        return true
    }

    fun unlockSportsTabWithCode(enteredCode: String): Boolean {
        val requiredCode = _appControlConfig.value?.fancodeCode ?: ""
        if (requiredCode.isNotBlank() && enteredCode.trim() == requiredCode.trim()) {
            _isSportsUnlockedLocally.value = true
            return true
        } else if (requiredCode.isBlank()) {
            _isSportsUnlockedLocally.value = true
            return true
        }
        return false
    }

    fun unlockAppWithFanCode(enteredCode: String): Boolean {
        val requiredCode = _appControlConfig.value?.fancodeCode ?: ""
        if (requiredCode.isBlank() || enteredCode.trim() == requiredCode.trim()) {
            _isAppUnlockedWithFanCode.value = true
            return true
        }
        return false
    }

    private var hasDismissedAppNoticeInSession = false

    fun dismissAppNotice() {
        hasDismissedAppNoticeInSession = true
        val current = _appControlConfig.value ?: return
        _appControlConfig.value = current.copy(notice = null)
    }

    fun fetchAppControlConfig(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingSuspension.value = true
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val controlUrls = listOf(
                "https://homeairtv-server.onrender.com/api/appControl",
                "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/appControl.json"
            )
            var configLoaded = false
            for (urlStr in controlUrls) {
                if (configLoaded) break
                try {
                    val req = okhttp3.Request.Builder()
                        .url(urlStr)
                        .header("Accept", "application/json")
                        .build()
                    client.newCall(req).execute().use { response ->
                        val body = response.body?.string()
                        if (response.isSuccessful && body != null && body.trim() != "null" && body.trim().startsWith("{")) {
                            val json = org.json.JSONObject(body)
                            val isSuspended = json.optBoolean("isAppSuspended", false)
                            val suspensionTitle = json.optString("suspensionTitle", "App Under Maintenance")
                            val suspensionMessage = json.optString("suspensionMessage", "App access is temporarily suspended by administrator. Please check back later.")
                            val isSportsLocked = json.optBoolean("isSportsTabLocked", false)
                            val sportsStatus = json.optString("sportsTabStatusText", "Live")
                            val sportsReason = json.optString("sportsLockReason", "Sports hub is currently locked by administrator.")
                            val fancodeCode = json.optString("fancodeCode", "")
                                .ifBlank { json.optString("fanCode", "") }
                                .ifBlank { json.optString("fancode", "") }
                                .ifBlank { json.optString("fan_code", "") }
                            val isFanCodeLocked = json.optBoolean("isFanCodeLocked", false) || json.optBoolean("isFanCodeRequired", false)
                            val isLiveTvLockEnabled = json.optBoolean("isLiveTvLockEnabled", false)

                            val lockedTabs = mutableListOf<String>()
                            val jsonLockedTabs = json.optJSONArray("lockedTabs")
                            if (jsonLockedTabs != null) {
                                for (i in 0 until jsonLockedTabs.length()) {
                                    lockedTabs.add(jsonLockedTabs.optString(i))
                                }
                            }

                            val premiumCategories = mutableListOf<String>()
                            val jsonPremCats = json.optJSONArray("premiumCategories")
                            if (jsonPremCats != null) {
                                for (i in 0 until jsonPremCats.length()) {
                                    premiumCategories.add(jsonPremCats.optString(i))
                                }
                            }

                            val premiumMediaIds = mutableListOf<String>()
                            val jsonPremIds = json.optJSONArray("premiumMediaIds")
                            if (jsonPremIds != null) {
                                for (i in 0 until jsonPremIds.length()) {
                                    premiumMediaIds.add(jsonPremIds.optString(i))
                                }
                            }

                            val premiumEmails = mutableListOf<String>()
                            val jsonPremEmails = json.optJSONArray("premiumEmails")
                            if (jsonPremEmails != null) {
                                for (i in 0 until jsonPremEmails.length()) {
                                    premiumEmails.add(jsonPremEmails.optString(i).trim().lowercase())
                                }
                            }

                            val freeEpisodeLimit = json.optInt("freeEpisodeLimit", 1)
                            val isPremiumRequired = json.optBoolean("isPremiumRequired", false)
                            val premiumPaywallTitle = json.optString("premiumPaywallTitle", "VIP Premium Subscription Required")
                            val premiumPaywallMessage = json.optString("premiumPaywallMessage", "This content or episode is reserved for Premium Subscribers. Please purchase a subscription to continue.")
                            val premiumPaywallButtonText = json.optString("premiumPaywallButtonText", "Buy Subscription Now")
                            val premiumPaywallButtonUrl = json.optString("premiumPaywallButtonUrl", "")

                            val isAdsEnabled = json.optBoolean("isAdsEnabled", false)
                            val adBannerUrl = json.optString("adBannerUrl", "")
                            val adClickUrl = json.optString("adClickUrl", "")
                            val adTitle = json.optString("adTitle", "Sponsored: Upgrade to VIP to Remove Ads")

                            val redeemCode = json.optString("redeemCode", "").ifBlank { json.optString("redeem_code", "") }
                            val redeemValidityHours = json.optInt("redeemValidityHours", 24)
                            val redeemExpiryTimestamp = json.optLong("redeemExpiryTimestamp", 0L)

                            val premiumLiveTvIds = mutableListOf<String>()
                            val jsonLiveTvIds = json.optJSONArray("premiumLiveTvIds")
                            if (jsonLiveTvIds != null) {
                                for (i in 0 until jsonLiveTvIds.length()) {
                                    premiumLiveTvIds.add(jsonLiveTvIds.optString(i))
                                }
                            }

                            val premiumLiveTvCategories = mutableListOf<String>()
                            val jsonLiveTvCats = json.optJSONArray("premiumLiveTvCategories")
                            if (jsonLiveTvCats != null) {
                                for (i in 0 until jsonLiveTvCats.length()) {
                                    premiumLiveTvCategories.add(jsonLiveTvCats.optString(i))
                                }
                            }

                            val noticeObj = json.optJSONObject("notice")
                            val notice = if (noticeObj != null) {
                                AppNotice(
                                    title = noticeObj.optString("title", ""),
                                    message = noticeObj.optString("message", ""),
                                    imageUrl = noticeObj.optString("imageUrl", null),
                                    buttonText = noticeObj.optString("buttonText", null),
                                    buttonUrl = noticeObj.optString("buttonUrl", null),
                                    isDismissible = noticeObj.optBoolean("isDismissible", true)
                                )
                            } else null

                            // Cache to SharedPreferences for instant cold-boot enforcement
                            try {
                                controlPrefs.edit()
                                    .putBoolean("isAppSuspended", isSuspended)
                                    .putString("suspensionTitle", suspensionTitle)
                                    .putString("suspensionMessage", suspensionMessage)
                                    .putBoolean("isSportsTabLocked", isSportsLocked)
                                    .putString("sportsTabStatusText", sportsStatus)
                                    .putString("sportsLockReason", sportsReason)
                                    .putString("fancodeCode", fancodeCode)
                                    .putBoolean("isFanCodeLocked", isFanCodeLocked)
                                    .putString("redeemCode", redeemCode)
                                    .putInt("redeemValidityHours", redeemValidityHours)
                                    .putLong("redeemExpiryTimestamp", redeemExpiryTimestamp)
                                    .apply()
                            } catch (e: Throwable) {
                                Log.e("StreamViewModel", "Error saving control preferences", e)
                            }

                            _appControlConfig.value = AppControlConfig(
                                isAppSuspended = isSuspended,
                                suspensionTitle = suspensionTitle,
                                suspensionMessage = suspensionMessage,
                                notice = if (hasDismissedAppNoticeInSession) null else notice,
                                isSportsTabLocked = isSportsLocked,
                                sportsTabStatusText = sportsStatus,
                                sportsLockReason = sportsReason,
                                fancodeCode = fancodeCode,
                                isFanCodeLocked = isFanCodeLocked,
                                lockedTabs = lockedTabs,
                                premiumCategories = premiumCategories,
                                premiumMediaIds = premiumMediaIds,
                                premiumEmails = premiumEmails,
                                freeEpisodeLimit = freeEpisodeLimit,
                                isPremiumRequired = isPremiumRequired,
                                premiumPaywallTitle = premiumPaywallTitle,
                                premiumPaywallMessage = premiumPaywallMessage,
                                premiumPaywallButtonText = premiumPaywallButtonText,
                                premiumPaywallButtonUrl = premiumPaywallButtonUrl,
                                isAdsEnabled = isAdsEnabled,
                                adBannerUrl = adBannerUrl,
                                adClickUrl = adClickUrl,
                                adTitle = adTitle,
                                redeemCode = redeemCode,
                                redeemValidityHours = redeemValidityHours,
                                redeemExpiryTimestamp = redeemExpiryTimestamp,
                                premiumLiveTvIds = premiumLiveTvIds,
                                premiumLiveTvCategories = premiumLiveTvCategories,
                                isLiveTvLockEnabled = isLiveTvLockEnabled
                            )

                            // Synchronize SubscriptionManager with remote config
                            com.example.subscription.SubscriptionManager.syncWithRemoteConfig(
                                emails = premiumEmails,
                                episodeLimit = freeEpisodeLimit
                            )

                            // If app was suspended while user is streaming or watching, immediately kill playback
                            if (isSuspended) {
                                withContext(Dispatchers.Main) {
                                    setMediaPlaying(false)
                                    setPlayerPlaying(false)
                                    _activeChannel.value = null
                                    _activeMediaItem.value = null
                                }
                            }

                            configLoaded = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StreamViewModel", "Error fetching app control config from $urlStr", e)
                }
            }
            _isCheckingSuspension.value = false
            _isInitialControlChecked.value = true
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    private val _sportsEventsState = MutableStateFlow<UiState<List<com.example.data.model.LiveMatch>>>(UiState.Loading)
    val sportsEventsState: StateFlow<UiState<List<com.example.data.model.LiveMatch>>> = _sportsEventsState.asStateFlow()

    private val _sportsChannelsState = MutableStateFlow<UiState<List<com.example.data.model.SportChannel>>>(UiState.Loading)
    val sportsChannelsState: StateFlow<UiState<List<com.example.data.model.SportChannel>>> = _sportsChannelsState.asStateFlow()

    fun fetchSportsData() {
        // Sync app remote control first
        fetchAppControlConfig()

        viewModelScope.launch(Dispatchers.IO) {
            _sportsEventsState.value = UiState.Loading
            _sportsChannelsState.value = UiState.Loading
            
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // 1. Fetch Sports Events (using Firebase Realtime Database)
            try {
                val req = okhttp3.Request.Builder()
                    .url("https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/sportsEvents.json")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        val list = mutableListOf<com.example.data.model.LiveMatch>()
                        if (body.trim().startsWith("[")) {
                            val arr = org.json.JSONArray(body)
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                if (!obj.optBoolean("isActive", true)) continue
                                
                                val id = obj.optString("id", i.toString())
                                val title = obj.optString("title")
                                val sportCategory = obj.optString("sportCategory")
                                val tournament = obj.optString("tournament", null)
                                val bannerUrl = obj.optString("bannerUrl", null)
                                val status = obj.optString("status")
                                val startTime = obj.optString("startTime", null)
                                val badgeText = obj.optString("badgeText", null)

                                val teamAObj = obj.optJSONObject("teamA")
                                val teamA = if (teamAObj != null) {
                                    com.example.data.model.Team(
                                        name = teamAObj.optString("name"),
                                        logo = teamAObj.optString("logo", null),
                                        score = teamAObj.optString("score", null)
                                    )
                                } else {
                                    com.example.data.model.Team("Team A", null, null)
                                }

                                val teamBObj = obj.optJSONObject("teamB")
                                val teamB = if (teamBObj != null) {
                                    com.example.data.model.Team(
                                        name = teamBObj.optString("name"),
                                        logo = teamBObj.optString("logo", null),
                                        score = teamBObj.optString("score", null)
                                    )
                                } else {
                                    com.example.data.model.Team("Team B", null, null)
                                }

                                val serversArr = obj.optJSONArray("servers")
                                val servers = mutableListOf<com.example.data.model.StreamServer>()
                                if (serversArr != null) {
                                    for (j in 0 until serversArr.length()) {
                                        val sObj = serversArr.optJSONObject(j) ?: continue
                                        servers.add(
                                            com.example.data.model.StreamServer(
                                                name = sObj.optString("name"),
                                                url = sObj.optString("url")
                                            )
                                        )
                                    }
                                }

                                list.add(
                                    com.example.data.model.LiveMatch(
                                        id = id,
                                        title = title,
                                        sportCategory = sportCategory,
                                        tournament = tournament,
                                        teamA = teamA,
                                        teamB = teamB,
                                        bannerUrl = bannerUrl,
                                        status = status,
                                        startTime = startTime,
                                        badgeText = badgeText,
                                        servers = servers
                                    )
                                )
                            }
                        } else {
                            val json = org.json.JSONObject(body)
                            val keys = json.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val obj = json.optJSONObject(key) ?: continue
                                if (!obj.optBoolean("isActive", true)) continue

                                val id = obj.optString("id", key)
                                val title = obj.optString("title")
                                val sportCategory = obj.optString("sportCategory")
                                val tournament = obj.optString("tournament", null)
                                val bannerUrl = obj.optString("bannerUrl", null)
                                val status = obj.optString("status")
                                val startTime = obj.optString("startTime", null)
                                val badgeText = obj.optString("badgeText", null)

                                val teamAObj = obj.optJSONObject("teamA")
                                val teamA = if (teamAObj != null) {
                                    com.example.data.model.Team(
                                        name = teamAObj.optString("name"),
                                        logo = teamAObj.optString("logo", null),
                                        score = teamAObj.optString("score", null)
                                    )
                                } else {
                                    com.example.data.model.Team("Team A", null, null)
                                }

                                val teamBObj = obj.optJSONObject("teamB")
                                val teamB = if (teamBObj != null) {
                                    com.example.data.model.Team(
                                        name = teamBObj.optString("name"),
                                        logo = teamBObj.optString("logo", null),
                                        score = teamBObj.optString("score", null)
                                    )
                                } else {
                                    com.example.data.model.Team("Team B", null, null)
                                }

                                val serversArr = obj.optJSONArray("servers")
                                val servers = mutableListOf<com.example.data.model.StreamServer>()
                                if (serversArr != null) {
                                    for (j in 0 until serversArr.length()) {
                                        val sObj = serversArr.optJSONObject(j) ?: continue
                                        servers.add(
                                            com.example.data.model.StreamServer(
                                                name = sObj.optString("name"),
                                                url = sObj.optString("url")
                                            )
                                        )
                                    }
                                }

                                list.add(
                                    com.example.data.model.LiveMatch(
                                        id = id,
                                        title = title,
                                        sportCategory = sportCategory,
                                        tournament = tournament,
                                        teamA = teamA,
                                        teamB = teamB,
                                        bannerUrl = bannerUrl,
                                        status = status,
                                        startTime = startTime,
                                        badgeText = badgeText,
                                        servers = servers
                                    )
                                )
                            }
                        }
                        _sportsEventsState.value = UiState.Success(list)
                    } else {
                        _sportsEventsState.value = UiState.Error("Failed to fetch sports events: code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("StreamViewModel", "Error fetching sports events", e)
                _sportsEventsState.value = UiState.Error(e.localizedMessage ?: "Unknown network error")
            }

            // 2. Fetch Sports Channels
            try {
                val req = okhttp3.Request.Builder()
                    .url("https://homeairtv-server.onrender.com/api/channels?category=sports")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        val json = org.json.JSONObject(body)
                        val arr = json.optJSONArray("channels") ?: org.json.JSONArray()
                        val list = mutableListOf<com.example.data.model.SportChannel>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            list.add(
                                com.example.data.model.SportChannel(
                                    id = obj.optString("id"),
                                    name = obj.optString("name"),
                                    url = obj.optString("url"),
                                    logo = obj.optString("logo", null),
                                    group = obj.optString("group", null),
                                    isPremium = obj.optBoolean("isPremium", false)
                                )
                            )
                        }
                        _sportsChannelsState.value = UiState.Success(list)
                    } else {
                        _sportsChannelsState.value = UiState.Error("Failed to fetch sports channels: code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("StreamViewModel", "Error fetching sports channels", e)
                _sportsChannelsState.value = UiState.Error(e.localizedMessage ?: "Unknown network error")
            }
        }
    }

    private val _activeMediaItem = MutableStateFlow<MediaItem?>(null)
    val activeMediaItem: StateFlow<MediaItem?> = _activeMediaItem.asStateFlow()
    
    private val _activeMediaStreamUrl = MutableStateFlow<String?>(null)
    val activeMediaStreamUrl: StateFlow<String?> = _activeMediaStreamUrl.asStateFlow()

    private val _activeMediaStreamHeaders = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeMediaStreamHeaders: StateFlow<Map<String, String>> = _activeMediaStreamHeaders.asStateFlow()
    
    private val _activeMediaSeason = MutableStateFlow(1)
    val activeMediaSeason: StateFlow<Int> = _activeMediaSeason.asStateFlow()

    private val _activeMediaEpisode = MutableStateFlow(1)
    val activeMediaEpisode: StateFlow<Int> = _activeMediaEpisode.asStateFlow()

    private val _playerAspectRatio = MutableStateFlow(16f / 9f)
    val playerAspectRatio: StateFlow<Float> = _playerAspectRatio.asStateFlow()

    // Anikoto Media State
    private val _anikotoDetailsState = MutableStateFlow<com.example.scraper.AnikotoDetails?>(null)
    val anikotoDetailsState: StateFlow<com.example.scraper.AnikotoDetails?> = _anikotoDetailsState.asStateFlow()

    private val _anikotoSeasons = MutableStateFlow<List<com.example.scraper.AnikotoSeason>>(emptyList())
    val anikotoSeasons: StateFlow<List<com.example.scraper.AnikotoSeason>> = _anikotoSeasons.asStateFlow()

    private val _anikotoEpisodes = MutableStateFlow<List<com.example.scraper.AnikotoEpisode>>(emptyList())
    val anikotoEpisodes: StateFlow<List<com.example.scraper.AnikotoEpisode>> = _anikotoEpisodes.asStateFlow()

    private val _isAnimeLoading = MutableStateFlow(false)
    val isAnimeLoading: StateFlow<Boolean> = _isAnimeLoading.asStateFlow()

    private val _isPlayerPlaying = MutableStateFlow(true)
    val isPlayerPlaying: StateFlow<Boolean> = _isPlayerPlaying.asStateFlow()

    private val _mediaPlaybackStartPosition = MutableStateFlow<Long?>(null)
    val mediaPlaybackStartPosition: StateFlow<Long?> = _mediaPlaybackStartPosition.asStateFlow()

    // Anikoto Scraped Server Selection State (SUB / DUB)
    private val _availableSubServers = MutableStateFlow<List<com.example.scraper.AnikotoServer>>(emptyList())
    val availableSubServers: StateFlow<List<com.example.scraper.AnikotoServer>> = _availableSubServers.asStateFlow()

    private val _availableDubServers = MutableStateFlow<List<com.example.scraper.AnikotoServer>>(emptyList())
    val availableDubServers: StateFlow<List<com.example.scraper.AnikotoServer>> = _availableDubServers.asStateFlow()

    private val _selectedServer = MutableStateFlow<com.example.scraper.AnikotoServer?>(null)
    val selectedServer: StateFlow<com.example.scraper.AnikotoServer?> = _selectedServer.asStateFlow()

    private val _isFetchingServers = MutableStateFlow(false)
    val isFetchingServers: StateFlow<Boolean> = _isFetchingServers.asStateFlow()

    var currentServerWatchUrl: String = ""
        private set

    fun selectAnikotoServer(server: com.example.scraper.AnikotoServer?) {
        _selectedServer.value = server
        val activeItem = _activeMediaItem.value
        if (server != null && activeItem != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val streamRes = com.example.scraper.AnikotoScraper.extractStreamFromServer(server, currentServerWatchUrl.ifBlank { activeItem.title })
                    if (streamRes != null && streamRes.streamUrl.isNotBlank()) {
                        _activeMediaStreamUrl.value = streamRes.streamUrl
                        _activeMediaStreamHeaders.value = streamRes.headers
                        _isPlayerPlaying.value = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun fetchAnikotoServers(item: MediaItem, season: Int = 1, episode: Int = 1) {
        viewModelScope.launch {
            _isFetchingServers.value = true
            try {
                val group = com.example.scraper.AnikotoScraper.fetchAvailableServers(
                    title = item.title,
                    season = season,
                    episode = episode
                )
                _availableSubServers.value = group.subServers
                _availableDubServers.value = group.dubServers
                currentServerWatchUrl = group.watchUrl

                // Auto-select server while preserving user's current SUB / DUB and server preference across episodes!
                val current = _selectedServer.value
                val isDub = current?.type?.lowercase() == "dub"
                val targetServers = if (isDub) group.dubServers else group.subServers
                val matched = targetServers.find { it.name.equals(current?.name, ignoreCase = true) }
                    ?: targetServers.firstOrNull()
                    ?: group.subServers.firstOrNull()
                    ?: group.dubServers.firstOrNull()

                _selectedServer.value = matched
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isFetchingServers.value = false
            }
        }
    }

    fun setMediaPlaybackStartPosition(pos: Long?) {
        _mediaPlaybackStartPosition.value = pos
    }

    fun saveMediaPlaybackProgress(mediaId: String, currentPosMs: Long, durationMs: Long) {
        if (mediaId.isBlank() || currentPosMs < 0) return
        try {
            val prefs = getApplication<Application>().getSharedPreferences("media_watch_progress", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("${mediaId}_pos", currentPosMs)
                .putLong("${mediaId}_dur", durationMs)
                .putLong("${mediaId}_time", System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {}
    }

    fun getMediaPlaybackProgress(mediaId: String): Triple<Long, Long, Long> {
        if (mediaId.isBlank()) return Triple(0L, 0L, 0L)
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("media_watch_progress", Context.MODE_PRIVATE)
            val pos = prefs.getLong("${mediaId}_pos", 0L)
            val dur = prefs.getLong("${mediaId}_dur", 0L)
            val time = prefs.getLong("${mediaId}_time", 0L)
            Triple(pos, dur, time)
        } catch (_: Exception) {
            Triple(0L, 0L, 0L)
        }
    }

    fun fuzzySearchMedia(query: String, items: List<MediaItem>): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        
        fun levenshteinDistance(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[0][j] = j
            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                }
            }
            return dp[s1.length][s2.length]
        }

        val qClean = q.replace(Regex("[^a-z0-9\\u0980-\\u09ff]"), "")
        val qWords = q.split(Regex("[^a-z0-9\\u0980-\\u09ff]+")).filter { it.isNotBlank() }

        return items.mapNotNull { item ->
            val titleLower = item.title.lowercase()
            val cleanTitle = titleLower.replace(Regex("[^a-z0-9\\u0980-\\u09ff]"), "")
            val titleWords = titleLower.split(Regex("[^a-z0-9\\u0980-\\u09ff]+")).filter { it.isNotBlank() }

            var score = 0
            if (titleLower == q) {
                score = 1000
            } else if (titleLower.startsWith(q)) {
                score = 800 + maxOf(0, 100 - titleLower.length)
            } else if (titleLower.contains(q)) {
                score = 600 + maxOf(0, 100 - titleLower.length)
            } else if (cleanTitle.contains(qClean) && qClean.length >= 3) {
                score = 550
            } else {
                val matchedWords = qWords.count { qw ->
                    titleWords.any { tw ->
                        tw.contains(qw) || qw.contains(tw) || (qw.length >= 4 && levenshteinDistance(qw, tw) <= (if (qw.length <= 5) 1 else 2))
                    }
                }
                if (matchedWords == qWords.size && qWords.isNotEmpty()) {
                    score = 450 + (matchedWords * 40)
                } else if (matchedWords > 0) {
                    score = 250 + (matchedWords * 30)
                } else if (qClean.length >= 4 && cleanTitle.length >= 4) {
                    val dist = levenshteinDistance(qClean, cleanTitle.take(minOf(cleanTitle.length, qClean.length + 2)))
                    if (dist <= 2) {
                        score = 400 - (dist * 50)
                    }
                }
            }

            if (score > 0) Pair(item, score) else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun setPlayerPlaying(playing: Boolean) {
        _isPlayerPlaying.value = playing
    }

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _isMiniPlayerMode = MutableStateFlow(false)
    val isMiniPlayerMode: StateFlow<Boolean> = _isMiniPlayerMode.asStateFlow()

    fun setInPipMode(value: Boolean) {
        _isInPipMode.value = value
    }

    fun setMiniPlayerMode(value: Boolean) {
        _isMiniPlayerMode.value = value
    }

    private val _isMediaPlaying = MutableStateFlow(false)
    val isMediaPlaying: StateFlow<Boolean> = _isMediaPlaying.asStateFlow()

    fun setMediaPlaying(value: Boolean) {
        _isMediaPlaying.value = value
    }

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    fun setSelectedTabIndex(index: Int) {
        _selectedTabIndex.value = index
    }

    private val _castState = MutableStateFlow<List<com.example.ui.components.CastMember>>(emptyList())
    val castState: StateFlow<List<com.example.ui.components.CastMember>> = _castState.asStateFlow()

    private val _isFetchingCast = MutableStateFlow(false)
    val isFetchingCast: StateFlow<Boolean> = _isFetchingCast.asStateFlow()

    private val _mediaDetailState = MutableStateFlow<com.example.data.network.TmdbMediaDetail?>(null)
    val mediaDetailState: StateFlow<com.example.data.network.TmdbMediaDetail?> = _mediaDetailState.asStateFlow()

    private val _youtubeVideoId = MutableStateFlow<String?>(null)
    val youtubeVideoId: StateFlow<String?> = _youtubeVideoId.asStateFlow()

    private val _youtubeTrailerId = MutableStateFlow<String?>(null)
    val youtubeTrailerId: StateFlow<String?> = _youtubeTrailerId.asStateFlow()

    fun fetchYouTubeTrailerId(title: String, year: String) {
        viewModelScope.launch {
            // First check if already loaded
            val initialDetails = _mediaDetailState.value
            val initialKey = initialDetails?.videos?.results
                ?.firstOrNull { it.site?.lowercase() == "youtube" && (it.type?.lowercase() == "trailer" || it.type?.lowercase() == "teaser") }
                ?.key
            if (!initialKey.isNullOrEmpty()) {
                _youtubeTrailerId.value = initialKey
                return@launch
            }

            // Wait for up to 1.5 seconds for details to load and provide a trailer
            var waitedMs = 0
            while (waitedMs < 1500) {
                kotlinx.coroutines.delay(150)
                waitedMs += 150
                val currentDetails = _mediaDetailState.value
                val currentKey = currentDetails?.videos?.results
                    ?.firstOrNull { it.site?.lowercase() == "youtube" && (it.type?.lowercase() == "trailer" || it.type?.lowercase() == "teaser") }
                    ?.key
                if (!currentKey.isNullOrEmpty()) {
                    _youtubeTrailerId.value = currentKey
                    return@launch
                }
            }

            // Fallback: Perform YouTube search
            val query = "$title $year official trailer"
            _youtubeTrailerId.value = mediaRepository.searchYouTube(query)
        }
    }

    fun clearYouTubeTrailerId() {
        _youtubeTrailerId.value = null
    }

    fun fetchYouTubeVideoId(title: String, year: String, season: Int = 1, episode: Int = 1, isSeries: Boolean = false) {
        viewModelScope.launch {
            val query = if (isSeries) {
                // More generic search for series to find official channels
                "$title Episode $episode Season $season Bangla"
            } else {
                "$title $year Bangla Cinema Full Movie"
            }
            _youtubeVideoId.value = mediaRepository.searchYouTube(query)
        }
    }

    fun clearYouTubeVideoId() {
        _youtubeVideoId.value = null
    }

    fun fetchMediaDetails(id: String, type: String) {
        viewModelScope.launch {
            _mediaDetailState.value = null
            _castState.value = emptyList()
            _isFetchingCast.value = true
            
            var isBangla = false
            val currentState = _mediaState.value
            if (currentState is UiState.Success) {
                val found = currentState.data.find { it.id == id || it.imdbId == id }
                if (found != null) {
                    val category = found.category ?: ""
                    val title = found.title ?: ""
                    isBangla = category.contains("Bangla", ignoreCase = true) || 
                               title.contains("Bangla", ignoreCase = true) ||
                               title.any { it.code in 0x0980..0x09FF }
                }
            }
            if (!isBangla && (id == "tt18281358" || id == "tt31526487" || id == "tt31526488" || id.startsWith("movie_2026") || id.startsWith("series_2026"))) {
                isBangla = true
            }

            try {
                val details = mediaRepository.fetchMediaDetails(id, type)
                _mediaDetailState.value = details
                val apiCast = details.credits?.cast?.take(15)?.map { cast ->
                    com.example.ui.components.CastMember(
                        name = cast.name ?: "Unknown",
                        character = cast.character ?: "",
                        profilePath = cast.profile_path
                    )
                } ?: emptyList()
                
                val hasHollywoodName = apiCast.any { cast ->
                    val nameLower = cast.name.lowercase()
                    nameLower.contains("holland") || nameLower.contains("zendaya") || nameLower.contains("ruffalo") || nameLower.contains("downey") || nameLower.contains("evans") || nameLower.contains("hemsworth") || nameLower.contains("pratt") || nameLower.contains("cumberbatch")
                }

                if (isBangla && (apiCast.isEmpty() || hasHollywoodName)) {
                    _castState.value = getBanglaFallbackCast()
                } else {
                    _castState.value = apiCast
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isBangla) {
                    _castState.value = getBanglaFallbackCast()
                }
            } finally {
                _isFetchingCast.value = false
            }
        }
    }

    private fun getBanglaFallbackCast(): List<com.example.ui.components.CastMember> {
        return listOf(
            com.example.ui.components.CastMember(
                name = "Shakib Khan",
                character = "Lead Actor",
                profilePath = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&q=80"
            ),
            com.example.ui.components.CastMember(
                name = "Mehazabien Chowdhury",
                character = "Lead Actress",
                profilePath = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&q=80"
            ),
            com.example.ui.components.CastMember(
                name = "Chanchal Chowdhury",
                character = "Supporting Actor / Antagonist",
                profilePath = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&q=80"
            ),
            com.example.ui.components.CastMember(
                name = "Mosharraf Karim",
                character = "Supporting Actor",
                profilePath = "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=200&q=80"
            ),
            com.example.ui.components.CastMember(
                name = "Afran Nisho",
                character = "Special Appearance",
                profilePath = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&q=80"
            )
        )
    }

    // Telemetry and Admin stats
    private val _telemetry = MutableStateFlow(TelemetryStats())
    val telemetry: StateFlow<TelemetryStats> = _telemetry.asStateFlow()

    // Notification triggers for Super Admin Dashboard
    private val _adminNotifications = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val adminNotifications: SharedFlow<String> = _adminNotifications.asSharedFlow()

    // AppsHub In-App Update Engine State
    private val _updateInfoState = MutableStateFlow<UpdateInfo?>(null)
    val updateInfoState: StateFlow<UpdateInfo?> = _updateInfoState.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("homeairtv_user_prefs", Context.MODE_PRIVATE)
    private var firebaseAuth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (e: Throwable) { null }
    private val firebaseAnalytics: FirebaseAnalytics? = try { FirebaseAnalytics.getInstance(application) } catch (e: Throwable) { null }

    fun logScreenView(screenName: String) {
        val bundle = android.os.Bundle().apply {
            putString("screen_name", screenName)
            putString("screen_class", "MainActivity")
        }
        firebaseAnalytics?.logEvent("screen_view", bundle)
    }

    fun logEvent(eventName: String, bundle: android.os.Bundle = android.os.Bundle()) {
        firebaseAnalytics?.logEvent(eventName, bundle)
    }

    // Blocked Users State
    private val _blockedUsers = MutableStateFlow<Set<String>>(
        sharedPrefs.getStringSet("blocked_users_set", emptySet()) ?: emptySet()
    )
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    // Persistent Settings StateFlows
    private val _bufferIndex = MutableStateFlow(sharedPrefs.getInt("setting_buffer_index", 1))
    val bufferIndex: StateFlow<Int> = _bufferIndex.asStateFlow()
    fun setBufferIndex(value: Int) {
        _bufferIndex.value = value
        sharedPrefs.edit().putInt("setting_buffer_index", value).apply()
    }

    private val _qualityIndex = MutableStateFlow(sharedPrefs.getInt("setting_quality_index", 1))
    val qualityIndex: StateFlow<Int> = _qualityIndex.asStateFlow()
    fun setQualityIndex(value: Int) {
        _qualityIndex.value = value
        sharedPrefs.edit().putInt("setting_quality_index", value).apply()
    }

    private val _audioIndex = MutableStateFlow(sharedPrefs.getInt("setting_audio_index", 0))
    val audioIndex: StateFlow<Int> = _audioIndex.asStateFlow()
    fun setAudioIndex(value: Int) {
        _audioIndex.value = value
        sharedPrefs.edit().putInt("setting_audio_index", value).apply()
        com.example.ui.theme.AppTranslation.applyAppLocale(getApplication(), value)
    }

    private val _themeIndex = MutableStateFlow(sharedPrefs.getInt("setting_theme_index", 0))
    val themeIndex: StateFlow<Int> = _themeIndex.asStateFlow()
    fun setThemeIndex(value: Int) {
        _themeIndex.value = value
        sharedPrefs.edit().putInt("setting_theme_index", value).apply()
        // Update global Compose theme color state instantly
        com.example.ui.theme.currentThemeIndex.value = value
    }

    private val _hardwareAccel = MutableStateFlow(sharedPrefs.getBoolean("setting_hardware_accel", true))
    val hardwareAccel: StateFlow<Boolean> = _hardwareAccel.asStateFlow()
    fun setHardwareAccel(value: Boolean) {
        _hardwareAccel.value = value
        sharedPrefs.edit().putBoolean("setting_hardware_accel", value).apply()
    }

    private val _autoPip = MutableStateFlow(sharedPrefs.getBoolean("setting_auto_pip", false))
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()
    fun setAutoPip(value: Boolean) {
        _autoPip.value = value
        sharedPrefs.edit().putBoolean("setting_auto_pip", value).apply()
    }

    private val _lowLatencySync = MutableStateFlow(sharedPrefs.getBoolean("setting_low_latency_sync", true))
    val lowLatencySync: StateFlow<Boolean> = _lowLatencySync.asStateFlow()
    fun setLowLatencySync(value: Boolean) {
        _lowLatencySync.value = value
        sharedPrefs.edit().putBoolean("setting_low_latency_sync", value).apply()
    }

    private val _batterySaverMode = MutableStateFlow(sharedPrefs.getBoolean("setting_battery_saver_mode", false))
    val batterySaverMode: StateFlow<Boolean> = _batterySaverMode.asStateFlow()
    fun setBatterySaverMode(value: Boolean) {
        _batterySaverMode.value = value
        sharedPrefs.edit().putBoolean("setting_battery_saver_mode", value).apply()
    }

    private val _maxFloatingPlayers = MutableStateFlow(sharedPrefs.getInt("setting_max_floating_players", 6))
    val maxFloatingPlayers: StateFlow<Int> = _maxFloatingPlayers.asStateFlow()
    fun setMaxFloatingPlayers(count: Int) {
        val clamped = count.coerceIn(2, 6)
        _maxFloatingPlayers.value = clamped
        sharedPrefs.edit().putInt("setting_max_floating_players", clamped).apply()
    }

    private val _activeFloatingPlayers = MutableStateFlow<List<FloatingPlayerInstance>>(emptyList())
    val activeFloatingPlayers: StateFlow<List<FloatingPlayerInstance>> = _activeFloatingPlayers.asStateFlow()

    fun addFloatingPlayer(
        channel: IptvChannel? = null,
        mediaItem: MediaItem? = null,
        streamUrl: String? = null,
        headers: Map<String, String> = emptyMap(),
        season: Int = 1,
        episode: Int = 1
    ) {
        val currentList = _activeFloatingPlayers.value.toMutableList()
        val limit = _maxFloatingPlayers.value.coerceIn(2, 6)

        val title = channel?.name ?: mediaItem?.title ?: "Player ${currentList.size + 1}"
        val isChan = channel != null

        // Premium protection check
        val currentEmail = _userProfile.value?.email
        if (!isUserPremium(currentEmail)) {
            if (channel != null && isChannelPremium(channel)) {
                triggerPremiumPaywall(true)
                return
            }
            if (mediaItem != null && !checkContentAccess(mediaItem.id, mediaItem.title, mediaItem.category, episode, currentEmail)) {
                return
            }
        }

        // Check if already playing this exact item to prevent duplicate streams
        val existing = currentList.find {
            (channel != null && it.channel?.url == channel.url) ||
            (mediaItem != null && it.mediaItem?.id == mediaItem.id && it.season == season && it.episode == episode)
        }
        if (existing != null) {
            return
        }

        if (currentList.size >= limit) {
            currentList.removeAt(0)
        }

        val offsetStep = (currentList.size * 40).toFloat()
        val newInstance = FloatingPlayerInstance(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            subtitle = if (isChan) (channel?.group ?: "Live TV") else "S${season}E${episode}",
            isChannel = isChan,
            channel = channel,
            mediaItem = mediaItem,
            streamUrl = streamUrl ?: channel?.url,
            headers = headers,
            season = season,
            episode = episode,
            isMuted = currentList.isNotEmpty(), // Mute subsequent streams to prevent sound clashing
            isPlaying = true,
            initialX = offsetStep,
            initialY = offsetStep + 80f
        )
        currentList.add(newInstance)
        _activeFloatingPlayers.value = currentList
        _isMiniPlayerMode.value = true
    }

    fun removeFloatingPlayer(id: String) {
        val updated = _activeFloatingPlayers.value.filter { it.id != id }
        _activeFloatingPlayers.value = updated
        if (updated.isEmpty()) {
            _isMiniPlayerMode.value = false
        }
    }

    fun toggleFloatingPlayerMute(id: String) {
        _activeFloatingPlayers.value = _activeFloatingPlayers.value.map {
            if (it.id == id) it.copy(isMuted = !it.isMuted) else it
        }
    }

    fun toggleFloatingPlayerPlay(id: String) {
        _activeFloatingPlayers.value = _activeFloatingPlayers.value.map {
            if (it.id == id) it.copy(isPlaying = !it.isPlaying) else it
        }
    }

    fun clearAllFloatingPlayers() {
        _activeFloatingPlayers.value = emptyList()
        _isMiniPlayerMode.value = false
    }

    fun popCurrentToFloating() {
        val ch = _activeChannel.value
        val med = _activeMediaItem.value
        val strUrl = _activeMediaStreamUrl.value
        val hdrs = _activeMediaStreamHeaders.value
        val s = _activeMediaSeason.value
        val ep = _activeMediaEpisode.value

        if (ch != null) {
            addFloatingPlayer(channel = ch)
        } else if (med != null) {
            addFloatingPlayer(mediaItem = med, season = s, episode = ep, streamUrl = strUrl, headers = hdrs)
        }
    }

    fun expandFloatingPlayerToMain(instance: FloatingPlayerInstance) {
        if (instance.isChannel && instance.channel != null) {
            setActiveChannel(instance.channel)
        } else if (instance.mediaItem != null) {
            playMediaItem(instance.mediaItem, instance.season, instance.episode)
        }
        removeFloatingPlayer(instance.id)
        setSelectedTabIndex(2)
    }

    init {
        try {
            com.example.ui.theme.currentThemeIndex.value = _themeIndex.value
            try {
                com.example.ui.theme.AppTranslation.applyAppLocale(application, _audioIndex.value)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            val database = AppDatabase.getDatabase(application)
            repository = StreamRepository(
                favoriteDao = database.favoriteDao(),
                historyDao = database.historyDao(),
                adminLogDao = database.adminLogDao(),
                mediaHistoryDao = database.mediaHistoryDao(),
                mediaFavoriteDao = database.mediaFavoriteDao(),
                context = application
            )

            // Restore active user session if available
            restoreUserSession()

            try {
                firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
            } catch (e: Throwable) {
                Log.e("StreamViewModel", "Error logging app open event", e)
            }

            // Load playlists & media at start
            loadPlaylists()
            loadMediaItems()

            // Check for AppsHub Store App Updates
            checkForAppUpdates(application)

            // Check App Remote Control & Kill-Switch immediately at app launch
            fetchAppControlConfig()
        } catch (e: Throwable) {
            Log.e("StreamViewModel", "Error initializing StreamViewModel", e)
        }

        // Periodic background poll for App Remote Control / Kill Switch (every 15 seconds)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                try {
                    fetchAppControlConfig()
                } catch (e: Throwable) {
                    // Ignore transient network errors
                }
            }
        }

        // Periodic app update checker while app is open
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000) // every 60 seconds
                try {
                    val result = AppUpdateManager.checkForUpdate(application)
                    if (result is UpdateCheckResult.UpdateAvailable) {
                        if (_updateInfoState.value == null || _updateInfoState.value?.latestVersionCode != result.info.latestVersionCode) {
                            _updateInfoState.value = result.info
                            AppUpdateManager.showUpdateNotification(application, result.info, AppUpdateManager.getAppVersionName(application))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("IptvParser", "Error fetching playlist", e)
                }
            }
        }

        // Start real-time admin telemetry ticks
        startTelemetryGenerator()
    }

    fun checkForAppUpdates(context: Context) {
        // Ensure background periodic update check is active even if app is closed
        AppUpdateWorker.schedulePeriodicCheck(context)

        viewModelScope.launch {
            when (val result = AppUpdateManager.checkForUpdate(context)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateInfoState.value = result.info
                }
                else -> {
                    _updateInfoState.value = null
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfoState.value = null
    }

    private fun restoreUserSession() {
        val fbUser = firebaseAuth?.currentUser
        if (fbUser != null) {
            val email = fbUser.email ?: "firebase.user@gmail.com"
            val name = fbUser.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
            val photoUrl = fbUser.photoUrl?.toString() ?: "https://api.dicebear.com/7.x/bottts/svg?seed=$email"
            val isAdmin = email.contains("admin") || email == "xubilas.era@gmail.com" || email == "hmairtv@gmail.com"

            try {
                firebaseAnalytics?.setUserId(email)
                firebaseAnalytics?.setUserProperty("user_name", name)
                firebaseAnalytics?.setUserProperty("is_super_admin", isAdmin.toString())
            } catch (e: Exception) {
                Log.e("IptvParser", "Error fetching playlist", e)
            }

            if (sharedPrefs.getString("user_id", "").isNullOrBlank()) {
                sharedPrefs.edit().putString("user_id", fbUser.uid).apply()
            }

            _userProfile.value = UserProfile(
                name = name,
                email = email,
                avatarUrl = photoUrl,
                isSuperAdmin = isAdmin,
                userId = fbUser.uid
            )
            com.example.subscription.SubscriptionManager.checkUserSubscription(email, fbUser.uid)
        } else {
            // If Firebase Auth has no active user, clear any old local cached session to prevent
            // a false logged-in state that would fail Firestore Security Rules.
            sharedPrefs.edit()
                .remove("user_email")
                .remove("user_name")
                .remove("user_avatar")
                .remove("user_id")
                .apply()
            _userProfile.value = null
        }
    }

    // Reactive DB maps
    val favorites: StateFlow<List<IptvChannel>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<IptvChannel>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaFavorites: StateFlow<List<com.example.data.model.MediaItem>> = repository.mediaFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaWatchHistory: StateFlow<List<com.example.data.model.MediaItem>> = repository.mediaWatchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminLogs: StateFlow<List<AdminLogEntity>> = repository.adminLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined/Filtered States (Optimized for Light-Speed 60fps Search)
    private val cleanRegex = Regex("[^a-z0-9\\u0980-\\u09ff]")
    private val tokenSplitRegex = Regex("\\s+")
    private val tokenCleanRegex = Regex("[^a-z0-9\\s\\u0980-\\u09ff]")

    val filteredPlaylists: StateFlow<List<IptvPlaylist>> = combine(_playlistsState, _playlistSearchQuery) { state, query ->
        if (state is UiState.Success) {
            val q = query.trim()
            if (q.isBlank()) state.data
            else state.data.filter { it.name.contains(q, ignoreCase = true) || it.group.contains(q, ignoreCase = true) }
        } else {
            emptyList()
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredChannels: StateFlow<List<IptvChannel>> = combine(_channelsState, _selectedChannelGroup, _channelSearchQuery) { state, group, query ->
        if (state is UiState.Success) {
            var list = state.data
            if (group != "All") {
                list = list.filter {
                    val g = it.group.ifBlank { "General" }
                    g.equals(group, ignoreCase = true)
                }
            }
            val q = query.trim()
            if (q.isNotBlank()) {
                list = list.filter { it.name.contains(q, ignoreCase = true) || it.group.contains(q, ignoreCase = true) }
            }
            list
        } else {
            emptyList()
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredMediaItems: StateFlow<List<MediaItem>> = combine(mediaState, _selectedMediaCategory, _mediaSearchQuery) { state, category, query ->
        if (state is UiState.Success) {
            var list = state.data
            val q = query.trim().lowercase()
            if (q.isBlank()) {
                if (category != "All") {
                    val catLower = category.lowercase()
                    list = list.filter { item ->
                        val itemCat = item.category.lowercase()
                        when {
                            catLower.contains("latest") || catLower.contains("new release") || catLower.contains("recent") -> itemCat.contains("latest") || (item.year.toIntOrNull() ?: 0) >= 2025
                            catLower.contains("hindi cinema") || catLower.contains("hindi movie") -> itemCat.contains("hindi cinema") || itemCat.contains("hindi movie")
                            catLower.contains("hindi series") -> itemCat.contains("hindi series")
                            catLower.contains("hindi dubbed k-drama") || catLower.contains("hindi dubbed kdrama") -> itemCat.contains("hindi dubbed k-dramas") || itemCat.contains("hindi dubbed kdrama")
                            catLower.contains("hindi dubbed") || catLower.contains("dubbed") -> itemCat.contains("dubbed") || itemCat.contains("hindi dubbed")
                            catLower.contains("romantic") || catLower.contains("romance") -> {
                                val romanceKeywords = listOf("romance", "romantic", "love", "lie", "kimi", "your name", "weathering", "horimiya", "kaguya", "dress-up", "silent voice", "toradora", "fruits basket", "clannad", "heart", "couple", "girlfriend", "darling")
                                itemCat.contains("anime") && romanceKeywords.any { (item.title + " " + item.description).lowercase().contains(it) }
                            }
                            catLower.contains("comedy") -> {
                                val comedyKeywords = listOf("comedy", "funny", "spy", "gintama", "kono suba", "saiki", "bocchi", "nichijou", "mashle", "shin-chan", "doraemon", "school", "devil", "humor", "parody")
                                itemCat.contains("anime") && comedyKeywords.any { (item.title + " " + item.description).lowercase().contains(it) }
                            }
                            catLower == "movies" -> itemCat == "movies"
                            catLower == "anime" || catLower.contains("anime") -> itemCat.contains("anime")
                            catLower.contains("series") || catLower.contains("tv") -> itemCat.contains("series") || itemCat.contains("tv")
                            catLower.contains("k-drama") || catLower.contains("kdrama") -> itemCat.contains("k-drama") || itemCat.contains("kdrama") || itemCat.contains("korean")
                            catLower == "action" -> itemCat == "action" || (itemCat.contains("anime") && listOf("action", "fight", "slayer", "titan", "hunter", "hero", "punch", "ninja", "dragon", "jujutsu", "bleach", "piece", "battle", "solo leveling").any { (item.title + " " + item.description).lowercase().contains(it) })
                            catLower.contains("sci-fi") || catLower.contains("scifi") -> itemCat.contains("sci-fi") || itemCat.contains("scifi")
                            else -> itemCat == catLower || itemCat.contains(catLower)
                        }
                    }
                }
            } else {
                // Search across all items starting from 1 character to any length
                val cleanQ = q.replace(cleanRegex, "")
                val queryWords = q.split(Regex("[\\s_\\-\\.:\\(\\)\\[\\]]+")).filter { it.isNotBlank() }
                val nonStopQueryWords = queryWords.filter { it.length > 2 && it != "the" && it != "and" && it != "all" }

                list = list.filter { item ->
                    val title = item.title.lowercase()
                    val titleClean = title.replace(cleanRegex, "")

                    // Exact full query match (highest priority, always include)
                    val fullMatch = title.contains(q) || (cleanQ.isNotBlank() && titleClean.contains(cleanQ))

                    // Word-based match: if we have non-stop query words, ALL of them must be present in the title
                    val wordMatch = if (nonStopQueryWords.isNotEmpty()) {
                        nonStopQueryWords.all { word -> 
                            title.contains(word) || titleClean.contains(word.replace(cleanRegex, ""))
                        }
                    } else {
                        // Fallback: if all words are very short, require all of them
                        queryWords.all { word -> 
                            title.contains(word)
                        }
                    }

                    fullMatch || wordMatch
                }.sortedByDescending { item ->
                    val title = item.title.lowercase()
                    val titleClean = title.replace(cleanRegex, "")
                    when {
                        title == q -> 1000
                        title.startsWith(q) -> 800
                        titleClean.startsWith(cleanQ) -> 750
                        title.contains(q) -> 600
                        else -> {
                            val matchCount = queryWords.count { title.contains(it) }
                            100 + matchCount * 10
                        }
                    }
                }
            }
            if (category.lowercase().contains("anime") && q.isBlank()) {
                list = sortAnimeList(list)
            } else if (category.equals("Bangla Cinema & Natok", ignoreCase = true) && q.isBlank()) {
                list = list.sortedWith(
                    compareByDescending<MediaItem> { it.year.toIntOrNull() ?: 0 }
                        .thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
                )
            }
            list
        } else {
            emptyList()
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sortAnimeList(items: List<MediaItem>): List<MediaItem> {
        val matureKeywords = listOf(
            "ecchi", "hentai", "erotica", "nude", "sex", "harem", "18+", "adult",
            "lust", "seduct", "fetish", "interspecies", "dxd", "redo of healer",
            "kiss x sis", "to love ru", "testament", "prison school"
        )
        val (mature, nonMature) = items.partition { item ->
            val text = (item.title + " " + item.description).lowercase()
            matureKeywords.any { text.contains(it) }
        }
        val familyActionKeywords = listOf(
            "spy", "family", "action", "comedy", "funny", "hero", "demon", "slayer",
            "naruto", "piece", "dragon", "ghibli", "doraemon", "pokemon", "detective",
            "titan", "hunter", "bleach", "kaisen", "jujutsu", "haikyu", "punch",
            "ninja", "academy", "conan", "shin-chan", "cat", "friend", "school",
            "game", "magic", "sports", "volleyball", "basketball", "thriller", "adventure"
        )
        val sortedNonMature = nonMature.sortedByDescending { item ->
            val text = (item.title + " " + item.description).lowercase()
            var score = 0
            familyActionKeywords.forEach { kw ->
                if (text.contains(kw)) score += 3
            }
            val ratingDouble = item.rating.toDoubleOrNull() ?: 0.0
            score += (ratingDouble * 2).toInt()
            score
        }
        return sortedNonMature + mature
    }

    fun loadMediaItems(forceRefresh: Boolean = false) {
        val currentState = _mediaState.value
        if (!forceRefresh && currentState is UiState.Success && currentState.data.isNotEmpty()) {
            return // Use in-memory cached results
        }
        backgroundPreloadJob?.cancel()
        _mediaState.value = UiState.Loading
        currentPage = 1
        loadedCategories.clear()

        backgroundPreloadJob = viewModelScope.launch {
            val progressiveCategories = listOf(
                "Latest",
                "Movies",
                "Series & TV Shows",
                "Bangla Cinema & Natok",
                "Anime Series",
                "Anime Movies",
                "K-Dramas",
                "Action",
                "Sci-Fi",
                "Hindi Cinema",
                "Hindi Series",
                "Hindi Dubbed",
                "Hindi Dubbed K-Dramas"
            )

            for (cat in progressiveCategories) {
                try {
                    val catItems = mediaRepository.fetchCategoryItems(cat, page = 1)
                    if (catItems.isNotEmpty()) {
                        loadedCategories.add(cat)
                        val currentSuccess = _mediaState.value as? UiState.Success
                        val currentList = currentSuccess?.data ?: emptyList()
                        val merged = (currentList + catItems).distinctBy { it.id }
                        _mediaState.value = UiState.Success(merged)
                    }
                } catch (e: Exception) {
                    Log.e("StreamViewModel", "Error progressively fetching $cat", e)
                }
                kotlinx.coroutines.delay(100)
            }

            if (_mediaState.value is UiState.Loading) {
                _mediaState.value = UiState.Error("Failed to fetch media items")
            }
        }
    }

    fun loadMoreMedia() {
        if (isLoadingMore) return
        val currentState = _mediaState.value
        if (currentState is UiState.Success) {
            isLoadingMore = true
            viewModelScope.launch {
                try {
                    val nextPage = currentPage + 1
                    val activeCategory = _selectedMediaCategory.value
                    val newItems = mediaRepository.fetchMoreMediaItems(page = nextPage, category = activeCategory)
                    if (newItems.isNotEmpty()) {
                        currentPage = nextPage
                        val currentList = currentState.data
                        val updatedList = (currentList + newItems).distinctBy { it.id }
                        _mediaState.value = UiState.Success(updatedList)
                    }
                } catch (e: Exception) {
                    Log.e("IptvParser", "Error fetching more media", e)
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    fun setSelectedMediaCategory(category: String) {
        _selectedMediaCategory.value = category
        // If the user selects a category that is not yet loaded, fetch it instantly to prevent blank states
        if (category != "All" && !loadedCategories.contains(category)) {
            viewModelScope.launch {
                try {
                    val catItems = mediaRepository.fetchCategoryItems(category, page = 1)
                    if (catItems.isNotEmpty()) {
                        loadedCategories.add(category)
                        val currentData = (_mediaState.value as? UiState.Success)?.data ?: emptyList()
                        val merged = (currentData + catItems).distinctBy { it.id }
                        _mediaState.value = UiState.Success(merged)
                    }
                } catch (e: Exception) {
                    Log.e("StreamViewModel", "Error fetching selected category $category", e)
                }
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    suspend fun searchMediaDirect(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val movies = mediaRepository.searchMedia(trimmed, "movie")
            val series = mediaRepository.searchMedia(trimmed, "series")
            val results = (movies + series).distinctBy { it.id }
            if (results.isNotEmpty()) {
                val currentData = (_mediaState.value as? UiState.Success)?.data ?: emptyList()
                val merged = (results + currentData).distinctBy { it.id }
                _mediaState.value = UiState.Success(merged)
            }
            results
        } catch (e: Exception) {
            Log.e("StreamViewModel", "Error in searchMediaDirect", e)
            emptyList()
        }
    }

    fun setMediaSearchQuery(query: String) {
        _mediaSearchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            com.example.data.repository.HomaiRepository.recordInterest(trimmed)
        }
        if (trimmed.isNotEmpty()) {
            searchJob = viewModelScope.launch {
                try {
                    val movies = mediaRepository.searchMedia(trimmed, "movie")
                    val series = mediaRepository.searchMedia(trimmed, "series")
                    val results = (movies + series).distinctBy { it.id }
                    if (results.isNotEmpty()) {
                        val currentData = (_mediaState.value as? UiState.Success)?.data ?: emptyList()
                        val merged = (results + currentData).distinctBy { it.id }
                        _mediaState.value = UiState.Success(merged)
                    }
                } catch (e: Exception) {
                    Log.e("StreamViewModel", "Error fetching search results", e)
                }
            }
        }
    }

    private var rawGithubPlaylistChannels: List<IptvChannel> = emptyList()
    private val rawGithubM3uUrl1 = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/refs/heads/main/movies.m3u8"
    private val rawGithubM3uUrl2 = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/refs/heads/main/hmairtv.m3u8"

    suspend fun ensureGithubRawPlaylistLoaded(): List<IptvChannel> {
        if (rawGithubPlaylistChannels.isNotEmpty()) {
            return rawGithubPlaylistChannels
        }
        return withContext(Dispatchers.IO) {
            val combined = mutableListOf<IptvChannel>()
            try {
                val raw1 = com.example.data.network.IptvParser.fetchRawContent(rawGithubM3uUrl1)
                combined.addAll(com.example.data.network.IptvParser.parseChannels(raw1))
            } catch (e: Exception) {
                Log.e("IptvParser", "Error fetching playlist", e)
            }
            try {
                val raw2 = com.example.data.network.IptvParser.fetchRawContent(rawGithubM3uUrl2)
                combined.addAll(com.example.data.network.IptvParser.parseChannels(raw2))
            } catch (e: Exception) {
                Log.e("IptvParser", "Error fetching playlist", e)
            }
            
            if (combined.isNotEmpty()) {
                rawGithubPlaylistChannels = combined
            }
            combined
        }
    }

    private fun isChannelMatchForMedia(channel: IptvChannel, item: MediaItem): Boolean {
        val cleanItemTitle = normalizeMediaTitle(item.title).replace(" ", "")
        val cleanChannelName = normalizeMediaTitle(channel.name).replace(" ", "")
        val cleanItemImdb = item.imdbId?.lowercase()?.trim() ?: ""

        if (cleanItemImdb.isNotEmpty() && cleanItemImdb.startsWith("tt")) {
            val chTvgId = channel.tvgId.lowercase().trim()
            if (chTvgId.contains(cleanItemImdb) || channel.url.lowercase().contains(cleanItemImdb)) {
                return true
            }
        }

        if (cleanItemTitle.isNotEmpty() && cleanChannelName.isNotEmpty()) {
            if (cleanChannelName == cleanItemTitle) return true
            
            // Contains match with safer length check
            if (cleanItemTitle.length >= 6 && cleanChannelName.length >= 6) {
                if (cleanChannelName.contains(cleanItemTitle) || cleanItemTitle.contains(cleanChannelName)) {
                    // Make sure it's not a sequel being matched by a shorter original movie
                    if (cleanChannelName.length > cleanItemTitle.length + 8 || cleanItemTitle.length > cleanChannelName.length + 8) {
                       // Do not return true directly, wait for word-level match which has sequel prevention 
                    } else {
                        return true
                    }
                }
            }
            
            // Word-level match
            val cleanItemTitleWithSpaces = normalizeMediaTitle(item.title)
            val cleanChannelNameWithSpaces = normalizeMediaTitle(channel.name)
            val itemWords = cleanItemTitleWithSpaces.split(" ").filter { it.isNotBlank() }
            val channelWords = cleanChannelNameWithSpaces.split(" ").filter { it.isNotBlank() }
            
            val stopWords = setOf("the", "and", "for", "with", "of", "in", "to", "is", "on", "at")
            val sigItemWords = itemWords.filter { it.length > 2 && !stopWords.contains(it) }.toSet()
            val sigChannelWords = channelWords.filter { it.length > 2 && !stopWords.contains(it) }.toSet()
            
            if (sigItemWords.isNotEmpty() && sigChannelWords.isNotEmpty()) {
                val matchedWords = sigItemWords.filter { w -> 
                    sigChannelWords.any { cw -> 
                        w == cw || (cw.length >= 4 && w.contains(cw)) || (w.length >= 4 && cw.contains(w))
                    } 
                }
                
                val ratio = matchedWords.size.toFloat() / sigItemWords.size
                
                // If it's a short title (1 word), require exact match of that word
                if (matchedWords.size == sigItemWords.size || (matchedWords.size >= 2 && ratio >= 0.59f)) {
                    if (sigItemWords.size == 1 && sigChannelWords.size > 1) {
                        // Single word title like "Avatar" must not blindly match "Avatar 2" or "Avatar Fire and Ash"
                    } else if (sigChannelWords.size <= sigItemWords.size + 1) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun normalizeMediaTitle(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("[^a-z0-9\\u0980-\\u09ff]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun fetchAnikotoMediaData(item: MediaItem, selectedSeasonNum: Int = 1) {
        val isAnime = item.id.startsWith("anikoto_") || item.category?.contains("Anime", ignoreCase = true) == true ||
                      item.type.equals("anime", ignoreCase = true) || item.title.lowercase().contains("naruto") || item.title.lowercase().contains("boruto")
        if (!isAnime) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAnimeLoading.value = true
            try {
                val watchUrl = if (item.streamUrl != null && item.streamUrl!!.isNotBlank() && item.streamUrl!!.contains("anikoto.cz")) {
                    item.streamUrl!!
                } else {
                    val searchResults = com.example.scraper.AnikotoScraper.searchOrFilterAnime(keyword = item.title)
                    val matched = searchResults.firstOrNull()
                    matched?.watchUrl ?: ""
                }

                if (watchUrl.isNotBlank()) {
                    Log.d("StreamViewModel", "Fetching Anikoto details & seasons for watchUrl: $watchUrl")
                    val details = com.example.scraper.AnikotoScraper.fetchAnimeDetails(watchUrl)
                    _anikotoDetailsState.value = details

                    val seasons = com.example.scraper.AnikotoScraper.fetchSeasons(watchUrl, item.title)
                    _anikotoSeasons.value = seasons

                    val matchedSeasonUrl = seasons.find { it.number == selectedSeasonNum }?.watchUrl ?: watchUrl
                    val episodes = com.example.scraper.AnikotoScraper.fetchEpisodes(matchedSeasonUrl)
                    _anikotoEpisodes.value = episodes
                    Log.d("StreamViewModel", "Anikoto details loaded: ${seasons.size} seasons, ${episodes.size} episodes")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnimeLoading.value = false
            }
        }
    }

    fun preScrapeMediaItem(item: MediaItem?, season: Int = 1, episode: Int = 1) {
        if (item == null) return
        val effectiveItem = item.copy(imdbId = item.imdbId ?: item.id)
        val tmdbId = effectiveItem.imdbId ?: effectiveItem.id
        if (com.example.scraper.UnifiedStreamManager.getCachedStream(tmdbId, season, episode) != null) return

        val isAnime = effectiveItem.category.lowercase().contains("anime") || effectiveItem.type.equals("anime", ignoreCase = true) ||
                      effectiveItem.title.lowercase().contains("naruto") || effectiveItem.title.lowercase().contains("boruto")

        val isSeriesItem = effectiveItem.type.equals("series", ignoreCase = true) ||
                           effectiveItem.type.equals("tv", ignoreCase = true) ||
                           (effectiveItem.type.equals("anime", ignoreCase = true) && !effectiveItem.category.lowercase().contains("movie"))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val streamResult = com.example.scraper.UnifiedStreamManager.getStream(
                    context = getApplication(),
                    title = effectiveItem.title,
                    tmdbId = tmdbId,
                    isTv = isSeriesItem,
                    season = season,
                    episode = episode,
                    isAnime = isAnime
                )
                if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                    if (_activeMediaItem.value?.id == item.id && _activeMediaStreamUrl.value.isNullOrBlank()) {
                        _activeMediaStreamUrl.value = streamResult.streamUrl
                        _activeMediaStreamHeaders.value = streamResult.headers
                        _isPlayerPlaying.value = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playMediaItem(item: MediaItem?, season: Int = 1, episode: Int = 1, startPositionMs: Long? = null) {
        if (item == null) {
            _activeChannel.value = null
            _activeMediaItem.value = null
            _isFullScreen.value = false
            _mediaPlaybackStartPosition.value = null
            return
        }

        val currentEmail = _userProfile.value?.email
        if (!checkContentAccess(
                mediaId = item.imdbId ?: item.id,
                title = item.title,
                category = item.category,
                episodeNum = episode,
                userEmail = currentEmail
            )) {
            return
        }

        val watchProgressKey = if (item.type.lowercase().contains("series") || item.type.lowercase().contains("tv")) {
            "${item.imdbId ?: item.id}_s${season}e${episode}"
        } else {
            item.imdbId ?: item.id
        }
        val effectiveStartPos = startPositionMs ?: getMediaPlaybackProgress(watchProgressKey).first
        _mediaPlaybackStartPosition.value = if (effectiveStartPos > 0L) effectiveStartPos else null

        viewModelScope.launch {
            _activeMediaSeason.value = season
            _activeMediaEpisode.value = episode
            val effectiveItem = item.copy(imdbId = item.imdbId ?: item.id)
            val tmdbId = effectiveItem.imdbId ?: effectiveItem.id

            // Check if user selected a specific Anikoto server (SUB or DUB)
            val pickedServer = _selectedServer.value
            if (pickedServer != null) {
                val serverStream = com.example.scraper.AnikotoScraper.extractStreamFromServer(
                    server = pickedServer,
                    watchUrl = currentServerWatchUrl
                )
                if (serverStream != null && serverStream.streamUrl.isNotBlank()) {
                    _activeChannel.value = null
                    _activeMediaItem.value = effectiveItem
                    _activeMediaStreamUrl.value = serverStream.streamUrl
                    _activeMediaStreamHeaders.value = serverStream.headers
                    _isPlayerPlaying.value = true
                    addMediaToHistory(effectiveItem)
                    return@launch
                }
            }

            // Check if stream is already pre-scraped / cached in background for instant launch!
            val cachedStream = com.example.scraper.UnifiedStreamManager.getCachedStream(tmdbId, season, episode)
            if (cachedStream != null && cachedStream.streamUrl.isNotBlank()) {
                _activeChannel.value = null
                _activeMediaItem.value = effectiveItem
                _activeMediaStreamUrl.value = cachedStream.streamUrl
                _activeMediaStreamHeaders.value = cachedStream.headers
                _isPlayerPlaying.value = true
                addMediaToHistory(effectiveItem)
                return@launch
            }

            val isAnime = effectiveItem.category.lowercase().contains("anime") || effectiveItem.type.equals("anime", ignoreCase = true) ||
                          effectiveItem.title.lowercase().contains("naruto") || effectiveItem.title.lowercase().contains("boruto")

            val isSeriesItem = effectiveItem.type.equals("series", ignoreCase = true) ||
                               effectiveItem.type.equals("tv", ignoreCase = true) ||
                               (effectiveItem.type.equals("anime", ignoreCase = true) && !effectiveItem.category.lowercase().contains("movie"))

            // Immediately launch background scraper in parallel
            val scraperJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val streamResult = com.example.scraper.UnifiedStreamManager.getStream(
                        context = getApplication(),
                        title = effectiveItem.title,
                        tmdbId = tmdbId,
                        isTv = isSeriesItem,
                        season = season,
                        episode = episode,
                        isAnime = isAnime
                    )
                    if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                        _activeMediaStreamUrl.value = streamResult.streamUrl
                        _activeMediaStreamHeaders.value = streamResult.headers
                        _isPlayerPlaying.value = true
                        repository.addLog(
                            type = "INFO",
                            title = "Direct Stream Scraped",
                            message = "Zero-Server Scraper extracted direct video link for '" + effectiveItem.title + "'"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _activeChannel.value = null
            _activeMediaItem.value = effectiveItem
            _activeMediaStreamUrl.value = null
            _activeMediaStreamHeaders.value = emptyMap()
            addMediaToHistory(effectiveItem)
        }
    }

    fun clearActivePlayer() {
        _activeChannel.value = null
        _activeMediaItem.value = null
        _isFullScreen.value = false
    }

    // Authentication Functions
    fun login(email: String, name: String, avatarUrl: String, uid: String = "") {
        viewModelScope.launch {
            val formattedEmail = email.trim().lowercase()
            val finalName = if (name.isNotBlank()) name else formattedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            val finalAvatar = avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/bottts/svg?seed=$formattedEmail" }
            val isAdmin = formattedEmail.contains("admin") || formattedEmail == "xubilas.era@gmail.com" || formattedEmail == "hmairtv@gmail.com"
            val finalUid = if (uid.isNotBlank()) uid else (firebaseAuth?.currentUser?.uid ?: "")

            sharedPrefs.edit()
                .putString("user_email", formattedEmail)
                .putString("user_name", finalName)
                .putString("user_avatar", finalAvatar)
                .putString("user_id", finalUid)
                .apply()
            
            registeredUserDao.insertUser(com.example.data.database.RegisteredUserEntity(
                email = formattedEmail,
                lastLogin = System.currentTimeMillis()
            ))

            _userProfile.value = UserProfile(
                name = finalName,
                email = formattedEmail,
                avatarUrl = finalAvatar,
                isSuperAdmin = isAdmin,
                userId = finalUid
            )

            repository.addLog(
                type = if (isAdmin) "SYSTEM" else "INFO",
                title = if (isAdmin) "Super Admin Signed In" else "Firebase & Google User Signed In",
                message = "Account: $formattedEmail authenticated successfully."
            )
        }
    }

    fun signInWithGoogle(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                
                // Retrieve google web client id dynamically from resources if available
                val webClientIdResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                val serverClientId = if (webClientIdResId != 0) {
                    context.getString(webClientIdResId)
                } else {
                    "312686001948-3rn45d4rv2f2a89qb4idp45plr5vdgp2.apps.googleusercontent.com"
                }

                if (serverClientId.isNotBlank()) {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(serverClientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential

                    if (credential is androidx.credentials.CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val idToken = googleIdTokenCredential.idToken
                            val email = googleIdTokenCredential.id
                            val displayName = googleIdTokenCredential.displayName ?: ""
                            val profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                            val firebaseAuthInstance = firebaseAuth
                            if (firebaseAuthInstance != null && idToken.isNotBlank()) {
                                val credentialObj = GoogleAuthProvider.getCredential(idToken, null)
                                firebaseAuthInstance.signInWithCredential(credentialObj)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val fbUser = firebaseAuthInstance.currentUser
                                            if (fbUser != null) {
                                                login(
                                                    email = fbUser.email ?: email,
                                                    name = fbUser.displayName ?: displayName,
                                                    avatarUrl = fbUser.photoUrl?.toString() ?: profilePictureUri,
                                                    uid = fbUser.uid
                                                )
                                                onSuccess()
                                                return@addOnCompleteListener
                                            }
                                        }
                                        val errorMsg = task.exception?.message ?: "Firebase authentication failed."
                                        Log.e("Auth", "Firebase sign-in failed: $errorMsg", task.exception)
                                        onError("Firebase sign-in failed: $errorMsg")
                                    }
                                return@launch
                            } else {
                                onError("Firebase authentication instance not available or ID token is empty.")
                                return@launch
                            }
                        } catch (e: com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException) {
                            onError("Invalid Google ID token.")
                            return@launch
                        }
                    }
                }

                // If no serverClientId or CredentialManager did not return GoogleIdTokenCredential, show error
                if (serverClientId.isBlank()) {
                    onError("Web Client ID is missing. Please redownload google-services.json from Firebase after adding SHA-1.")
                } else {
                    onError("Google Sign-In failed. Please try again.")
                }

            } catch (e: Exception) {
                Log.e("IptvParser", "Error fetching playlist", e)
                android.util.Log.e("GoogleSignIn", "CredentialManager Error: ${e.message}", e)
                if (e is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    onError("Google Sign-In canceled.")
                } else {
                    onError("Google Sign-In Error: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val email = _userProfile.value?.email ?: "Unknown"
            try {
                firebaseAuth?.signOut()
            } catch (e: Exception) {
                Log.e("IptvParser", "Error fetching playlist", e)
            }
            sharedPrefs.edit().clear().apply()

            repository.addLog("INFO", "User Logged Out", "Account: $email signed out.")
            _userProfile.value = null
            _selectedPlaylist.value = null
            _channelsState.value = UiState.Success(emptyList())
            _activeChannel.value = null
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            val email = _userProfile.value?.email ?: "Unknown"
            try {
                firebaseAuth?.currentUser?.delete()
                firebaseAuth?.signOut()
            } catch (e: Exception) {
                Log.e("IptvParser", "Error fetching playlist", e)
            }
            sharedPrefs.edit().clear().apply()

            repository.addLog("WARN", "Account Deleted", "User account ($email) was deleted.")
            _userProfile.value = null
            _selectedPlaylist.value = null
            _channelsState.value = UiState.Success(emptyList())
            _activeChannel.value = null
            onComplete()
        }
    }

    fun toggleBlockUser(email: String) {
        viewModelScope.launch {
            val current = _blockedUsers.value.toMutableSet()
            val isNowBlocked: Boolean
            if (current.contains(email)) {
                current.remove(email)
                isNowBlocked = false
                repository.addLog("INFO", "User Unblocked", "Admin unblocked account: $email")
            } else {
                current.add(email)
                isNowBlocked = true
                repository.addLog("WARN", "User Blocked / Restricted", "Admin blocked account: $email")
            }
            _blockedUsers.value = current
            sharedPrefs.edit().putStringSet("blocked_users_set", current).apply()

            // If current user was blocked, log them out
            if (isNowBlocked && _userProfile.value?.email.equals(email, ignoreCase = true)) {
                logout()
            }
        }
    }

    fun isUserBlocked(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return _blockedUsers.value.contains(email)
    }

    fun sendBroadcastNotification(context: Context, title: String, message: String) {
        viewModelScope.launch {
            com.example.notifications.LocalNotificationManager.showNotification(
                context = context,
                title = title,
                message = message,
                notificationId = (System.currentTimeMillis() % 10000).toInt()
            )
            repository.addLog("ALERT", "Broadcast Notification Sent", "Title: '$title', Body: '$message'")
        }
    }

    // Trigger unauthorized access log
    fun logUnauthorizedAccessAttempt(accessedScreen: String) {
        viewModelScope.launch {
            _telemetry.update { it.copy(unauthorizedAccessAttempts = it.unauthorizedAccessAttempts + 1) }
            repository.addLog(
                type = "ALERT",
                title = "Unauthorized Access attempt",
                message = "Guest or unauthorized user tried to open: $accessedScreen"
            )
            _adminNotifications.tryEmit("CRITICAL: Unauthorized access attempt detected on '$accessedScreen'!")
        }
    }

    private var backupChannels: List<IptvChannel> = emptyList()

    fun getBackupChannel(name: String): IptvChannel? {
        if (backupChannels.isEmpty()) {
            val url = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8"
            viewModelScope.launch {
                try {
                    backupChannels = repository.getChannelsFromPlaylist(url)
                } catch (e: Exception) {
                    Log.e("IptvParser", "Error fetching playlist", e)
                }
            }
            return null
        }
        val cleanName = name.trim().lowercase().replace(" ", "")
        var match = backupChannels.find { it.name.trim().lowercase().replace(" ", "") == cleanName }
        if (match != null) return match

        match = backupChannels.find {
            val otherName = it.name.lowercase()
            val selfName = name.lowercase()
            otherName.contains(selfName) || selfName.contains(otherName)
        }
        return match
    }

    // Loading Main Playlists
    fun loadPlaylists(forceRefresh: Boolean = false) {
        val currentState = _playlistsState.value
        if (!forceRefresh && currentState is UiState.Success && currentState.data.isNotEmpty()) {
            return // Instantaneous load from memory!
        }
        viewModelScope.launch {
            _playlistsState.value = UiState.Loading
            try {
                val data = repository.getIndexPlaylists(forceRefresh)
                _playlistsState.value = UiState.Success(data)
                // Do not auto-select playlist on startup so app opens on home page dashboard
                if (data.isNotEmpty() && _selectedPlaylist.value == null) {
                    val firstPlaylist = data.first()
                    launch {
                        try {
                            backupChannels = repository.getChannelsFromPlaylist(firstPlaylist.url, forceRefresh)
                        } catch (e: Exception) {
                            Log.e("IptvParser", "Error fetching playlist", e)
                        }
                    }
                }
            } catch (e: Exception) {
                _playlistsState.value = UiState.Error(e.localizedMessage ?: "Unknown Error loading index")
            }
        }
    }

    fun setPlaylistSearchQuery(query: String) {
        _playlistSearchQuery.value = query
    }

    // Selecting a Sub-Playlist to view its channels
    fun selectPlaylist(playlist: IptvPlaylist) {
        _selectedPlaylist.value = playlist
        _channelSearchQuery.value = ""
        _selectedChannelGroup.value = "All"
        loadChannels(playlist.url)
    }

    fun clearSelectedPlaylist() {
        _selectedPlaylist.value = null
        _selectedChannelGroup.value = "All"
    }

    private fun loadChannels(url: String) {
        viewModelScope.launch {
            _channelsState.value = UiState.Loading
            try {
                var data = repository.getChannelsFromPlaylist(url)
                
                val currentPlaylist = _selectedPlaylist.value
                val playlistName = currentPlaylist?.name?.lowercase() ?: ""
                
                if (playlistName.contains("sport tv")) {
                    // Filter T Sports channels case-insensitively
                    var tsportsChannels = data.filter { 
                        it.name.contains("T Sports", ignoreCase = true) || 
                        it.name.contains("TSports", ignoreCase = true) 
                    }
                    
                    // If not found in sports.m3u, search in backupChannels (abidverse)
                    if (tsportsChannels.isEmpty()) {
                        if (backupChannels.isEmpty()) {
                            try {
                                val abidUrl = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8"
                                backupChannels = repository.getChannelsFromPlaylist(abidUrl)
                            } catch (e: Exception) {
                                Log.e("IptvParser", "Error fetching playlist", e)
                            }
                        }
                        tsportsChannels = backupChannels.filter { 
                            it.name.contains("T Sports", ignoreCase = true) || 
                            it.name.contains("TSports", ignoreCase = true) 
                        }
                    }
                    
                    // Fallback local stream link if not found in either
                    if (tsportsChannels.isEmpty()) {
                        tsportsChannels = listOf(
                            IptvChannel(
                                name = "T Sports (1080)",
                                url = "http://103.204.145.242:8000/tsports/index.m3u8",
                                logo = "https://img.icons8.com/color/96/sport.png",
                                group = "Sports"
                            )
                        )
                    } else {
                        // Ensure name is exactly T Sports (1080)
                        tsportsChannels = tsportsChannels.map { 
                            it.copy(name = "T Sports (1080)")
                        }
                    }
                    data = tsportsChannels
                } else if (playlistName.contains("bangladesh") || playlistName.contains("india")) {
                    if (backupChannels.isEmpty()) {
                        try {
                            val abidUrl = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8"
                            backupChannels = repository.getChannelsFromPlaylist(abidUrl)
                        } catch (e: Exception) {
                            Log.e("IptvParser", "Error fetching playlist", e)
                        }
                    }
                    if (playlistName.contains("bangladesh")) {
                        val bdKeywords = listOf("bangla", "bd", "btv", "somoy", "ekattor", "independent", "jamuna", "ntv", "rtv", "deepto", "nagorik", "gtv", "sports", "aamar", "maasranga", "mohona", "sa tv", "dbc", "channel 24", "desh", "my tv", "asian", "boishakhi", "bijoy", "global")
                        val updateBdChannels = backupChannels.filter { channel ->
                            val text = (channel.name + " " + channel.group).lowercase()
                            bdKeywords.any { text.contains(it) }
                        }
                        data = (updateBdChannels + data).distinctBy { it.name.trim().lowercase() }
                    } else if (playlistName.contains("india")) {
                        val inKeywords = listOf("india", "in", "hindi", "tamil", "telugu", "zee", "star", "sony", "colors", "aaj tak", "ndtv", "republic", "sun tv", "asianet", "vijay", "goldmines", "abp", "news18", "times now", "dd", "kolkata", "jalsa")
                        val updateInChannels = backupChannels.filter { channel ->
                            val text = (channel.name + " " + channel.group).lowercase()
                            inKeywords.any { text.contains(it) }
                        }
                        data = (updateInChannels + data).distinctBy { it.name.trim().lowercase() }
                    }
                }
                
                _channelsState.value = UiState.Success(data)
            } catch (e: Exception) {
                val currentPlaylist = _selectedPlaylist.value
                val playlistName = currentPlaylist?.name?.lowercase() ?: ""
                if (playlistName.contains("sport tv")) {
                    val fallback = listOf(
                        IptvChannel(
                            name = "T Sports (1080)",
                            url = "http://103.204.145.242:8000/tsports/index.m3u8",
                            logo = "https://img.icons8.com/color/96/sport.png",
                            group = "Sports"
                        )
                    )
                    _channelsState.value = UiState.Success(fallback)
                } else if ((playlistName.contains("bangladesh") || playlistName.contains("india")) && backupChannels.isNotEmpty()) {
                    val filtered = if (playlistName.contains("bangladesh")) {
                        val bdKeywords = listOf("bangla", "bd", "btv", "somoy", "ekattor", "independent", "jamuna", "ntv", "rtv", "deepto", "nagorik", "gtv", "sports", "aamar", "maasranga", "mohona", "sa tv", "dbc", "channel 24", "desh", "my tv", "asian", "boishakhi", "bijoy", "global")
                        backupChannels.filter { channel ->
                            val text = (channel.name + " " + channel.group).lowercase()
                            bdKeywords.any { text.contains(it) }
                        }
                    } else {
                        val inKeywords = listOf("india", "in", "hindi", "tamil", "telugu", "zee", "star", "sony", "colors", "aaj tak", "ndtv", "republic", "sun tv", "asianet", "vijay", "goldmines", "abp", "news18", "times now", "dd", "kolkata", "jalsa")
                        backupChannels.filter { channel ->
                            val text = (channel.name + " " + channel.group).lowercase()
                            inKeywords.any { text.contains(it) }
                        }
                    }
                    if (filtered.isNotEmpty()) {
                        _channelsState.value = UiState.Success(filtered)
                    } else {
                        _channelsState.value = UiState.Error(e.localizedMessage ?: "Unknown Error loading channels")
                    }
                } else {
                    _channelsState.value = UiState.Error(e.localizedMessage ?: "Unknown Error loading channels")
                }
            }
        }
    }

    fun setChannelSearchQuery(query: String) {
        _channelSearchQuery.value = query
    }

    // Video Player Playback Control
    fun setActiveChannel(channel: IptvChannel?) {
        _playbackErrorChannelUrl.value = null
        if (channel != null) {
            val currentEmail = _userProfile.value?.email
            if (!isUserPremium(currentEmail)) {
                // Live TV specific channel and category lock (completely isolated from movie/anime locks)
                if (isChannelPremium(channel)) {
                    triggerPremiumPaywall(true)
                    return
                }
            }
        }
        viewModelScope.launch {
            _activeMediaItem.value = null
            _activeChannel.value = channel
            _isPlayerPlaying.value = true
            if (channel != null) {
                repository.addToHistory(channel)
                repository.addLog(
                    type = "INFO",
                    title = "Playback Started",
                    message = "Streaming channel: ${channel.name} (${channel.url})"
                )
            }
        }
    }

    fun playHomeAirTvDirect(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _channelsState.value = UiState.Loading
            val playlistUrl = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/homeairtv.m3u"
            val directPlaylist = IptvPlaylist(
                name = "Home Air TV Live",
                url = playlistUrl,
                group = "Live",
                logo = "https://img.icons8.com/color/96/live-video.png"
            )
            _selectedPlaylist.value = directPlaylist
            _selectedChannelGroup.value = "All"
            
            try {
                var channels = repository.getChannelsFromPlaylist(playlistUrl)
                if (channels.isEmpty()) {
                    // Try fallback hmairtv.m3u8 if empty
                    val fallbackUrl = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8"
                    channels = repository.getChannelsFromPlaylist(fallbackUrl)
                }
                
                if (channels.isNotEmpty()) {
                    _channelsState.value = UiState.Success(channels)
                    setActiveChannel(channels.first())
                    onComplete()
                } else {
                    _channelsState.value = UiState.Error("No channels found in playlist.")
                }
            } catch (e: Exception) {
                Log.e("IptvParser", "Error loading direct homeairtv.m3u", e)
                try {
                    val fallbackUrl = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8"
                    val fallbackChannels = repository.getChannelsFromPlaylist(fallbackUrl)
                    if (fallbackChannels.isNotEmpty()) {
                        _channelsState.value = UiState.Success(fallbackChannels)
                        setActiveChannel(fallbackChannels.first())
                        onComplete()
                    } else {
                        _channelsState.value = UiState.Error("Error loading channels: ${e.localizedMessage}")
                    }
                } catch (ex: Exception) {
                    _channelsState.value = UiState.Error("Error loading channels: ${e.localizedMessage}")
                }
            }
        }
    }

    fun playNextChannel() {
        val current = _activeChannel.value ?: return
        val list = filteredChannels.value
        if (list.isNotEmpty()) {
            val currentIndex = list.indexOfFirst { it.url == current.url }
            if (currentIndex != -1) {
                val nextIndex = (currentIndex + 1) % list.size
                setActiveChannel(list[nextIndex])
            } else {
                setActiveChannel(list.first())
            }
        }
    }

    fun playPrevChannel() {
        val current = _activeChannel.value ?: return
        val list = filteredChannels.value
        if (list.isNotEmpty()) {
            val currentIndex = list.indexOfFirst { it.url == current.url }
            if (currentIndex != -1) {
                val prevIndex = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
                setActiveChannel(list[prevIndex])
            } else {
                setActiveChannel(list.first())
            }
        }
    }

    fun logPlaybackError(channelName: String, error: String) {
        _playbackErrorChannelUrl.value = _activeChannel.value?.url
        viewModelScope.launch {
            repository.addLog(
                type = "ERROR",
                title = "Stream Failure Logged",
                message = "Channel '$channelName' failed: $error"
            )
        }
    }

    fun clearPlaybackError() {
        _playbackErrorChannelUrl.value = null
    }

    fun setAspectRatio(ratio: Float) {
        _playerAspectRatio.value = ratio
    }

    // Favorites Interaction
    fun toggleFavorite(channel: IptvChannel, isFav: Boolean) {
        viewModelScope.launch {
            if (isFav) {
                repository.removeFavorite(channel)
            } else {
                repository.addFavorite(channel)
            }
        }
    }

    fun isFavoriteStream(url: String): Flow<Boolean> {
        return repository.isFavorite(url)
    }

    fun isChannelPremium(channel: IptvChannel): Boolean {
        val config = _appControlConfig.value ?: return false
        val cleanName = channel.name.trim()
        val cleanUrl = channel.url.trim()
        val cleanGroup = channel.group.trim().lowercase()
        val cleanTvgId = channel.tvgId.trim()
        
        if (config.isLiveTvLockEnabled) {
            // Check individual channel flag from API
            if (channel.isPremium) return true

            // 1. Check if group/category is in specific Live TV premium categories (completely separate from movie/anime categories)
            if (cleanGroup.isNotBlank() && config.premiumLiveTvCategories.any { cleanGroup.contains(it.lowercase()) }) {
                return true
            }
            
            // 2. Check if channel name, URL, or tvgId matches any specific Live TV premium channel IDs/names
            if (config.premiumLiveTvIds.any {
                cleanName.contains(it, ignoreCase = true) ||
                cleanUrl.contains(it, ignoreCase = true) ||
                (cleanTvgId.isNotBlank() && cleanTvgId.equals(it.trim(), ignoreCase = true))
            }) {
                return true
            }
        }
        // 3. Fallback to standard admin panel lock fields (premiumCategories, lockedTabs, premiumMediaIds)
        if (cleanGroup.isNotBlank() && config.premiumCategories.any { cleanGroup.contains(it.lowercase()) }) {
            return true
        }
        if (cleanGroup.isNotBlank() && config.lockedTabs.any { cleanGroup.contains(it.lowercase()) }) {
            return true
        }
        if (config.premiumMediaIds.any {
            cleanName.contains(it, ignoreCase = true) ||
            cleanUrl.contains(it, ignoreCase = true) ||
            (cleanTvgId.isNotBlank() && cleanTvgId.equals(it.trim(), ignoreCase = true))
        }) {
            return true
        }
        
        return false
    }

    fun toggleMediaFavorite(item: com.example.data.model.MediaItem, isFav: Boolean) {
        viewModelScope.launch {
            if (isFav) {
                repository.removeMediaFavorite(item)
            } else {
                repository.addMediaFavorite(item)
            }
        }
    }

    fun isMediaFavoriteStream(id: String): Flow<Boolean> {
        return repository.isMediaFavorite(id)
    }

    fun addMediaToHistory(item: com.example.data.model.MediaItem) {
        viewModelScope.launch {
            repository.addMediaToHistory(item)
        }
    }

    fun clearMediaWatchHistory() {
        viewModelScope.launch {
            repository.clearMediaHistory()
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearAdminLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // Simulated Telemetry generator for high-tech Super Admin dashboard
    private fun startTelemetryGenerator() {
        viewModelScope.launch {
            while (true) {
                delay(3000) // update stats every 3 seconds
                
                // Pulsing simulator
                val userDiff = (-30..30).random()
                val pingDiff = (-5..5).random()
                val cpuDiff = (-2..2).random()
                val memDiff = (-1..1).random()
                val bwDiff = (-15..15).random() / 10.0

                _telemetry.update { current ->
                    val newLiveUsers = (current.liveUsers + userDiff).coerceIn(98000, 115000)
                    val newVisited = current.visitedRealUsers + (0..3).random()
                    val newPing = (current.cdnResponseMs + pingDiff).coerceIn(20, 95)
                    val newCpu = (current.cpuUsage + cpuDiff).coerceIn(15, 80)
                    val newMem = (current.memoryUsage + memDiff).coerceIn(40, 75)
                    val newBw = (current.activeBandwidthGbps + bwDiff).coerceAtLeast(100.0)

                    // Determine health based on simulated CPU/latency
                    val newHealth = when {
                        newCpu > 75 || newPing > 80 -> {
                            // Disabled warning notification about edge CDN node latency exceeded 80ms per user request
                            "CRITICAL"
                        }
                        newCpu > 60 || newPing > 60 -> "WARNING"
                        else -> "HEALTHY"
                    }

                    // Add simulated system logs if state gets worse
                    if (newCpu > 70 && (1..10).random() == 1) {
                        repository.addLog(
                            type = "ALERT",
                            title = "High Server Load",
                            message = "Edge CDN node 'EU-West' reached $newCpu% CPU utilization."
                        )
                    }

                    current.copy(
                        liveUsers = newLiveUsers,
                        visitedRealUsers = newVisited,
                        cdnResponseMs = newPing,
                        cpuUsage = newCpu,
                        memoryUsage = newMem,
                        activeBandwidthGbps = newBw,
                        serverHealth = newHealth
                    )
                }
            }
        }
    }

    // --- HOMAI AI ASSISTANT STATE & METHODS ---
    private val homaiRepository = HomaiRepository()

    private val defaultHomaiWelcome = "👋 Hello! I am Homai, your official HomeAir TV AI Assistant.\n\nHow can I help you today? Ask me for live channels, sports matches, movies, anime, or streaming help!\n\n💡 Tip: If a channel or media stream doesn't play on the first attempt, close the player and try tapping it again or select another stream."

    private val _homaiMessages = MutableStateFlow<List<HomaiMessage>>(
        listOf(
            HomaiMessage(
                isUser = false,
                text = defaultHomaiWelcome
            )
        )
    )
    val homaiMessages: StateFlow<List<HomaiMessage>> = _homaiMessages.asStateFlow()

    private val _isHomaiThinking = MutableStateFlow(false)
    val isHomaiThinking: StateFlow<Boolean> = _isHomaiThinking.asStateFlow()

    private val _isHomaiSheetVisible = MutableStateFlow(false)
    val isHomaiSheetVisible: StateFlow<Boolean> = _isHomaiSheetVisible.asStateFlow()

    fun openHomaiChat() {
        _isHomaiSheetVisible.value = true
    }

    fun closeHomaiChat() {
        _isHomaiSheetVisible.value = false
    }

    fun toggleHomaiChat() {
        _isHomaiSheetVisible.value = !_isHomaiSheetVisible.value
    }

    private val homaiStopWords = setOf(
        "video", "play", "playing", "hosce", "na", "keno", "ki", "kivabe", "how", "why", "what",
        "where", "when", "is", "are", "was", "were", "the", "this", "that", "it", "not", "me",
        "tell", "show", "can", "you", "please", "help", "app", "problem", "issue", "fix", "working",
        "work", "error", "dekhaw", "ache", "ভিডিও", "প্লে", "হচ্ছে", "না", "কেন", "কী", "কি", "কীভাবে", "কেমনে",
        "বলো", "দেখাও", "সমস্যা", "কাজ", "করছে", "চলছে", "আছে", "a", "an", "to", "in", "on", "for"
    )

    private val homaiQuestionKeywords = setOf(
        "how", "download", "downlaod", "guide", "kivabe", "ki", "kivab", "banaice", "who", "made",
        "creator", "developer", "privacy", "policy", "terms", "rules", "fix", "troubleshoot",
        "m3u", "playlist", "step", "help", "সাহায্য", "ডাউনলোড", "কীভাবে", "কেমন", "সমস্যা", "প্রশ্ন"
    )

    private fun extractSearchTerms(query: String): List<String> {
        val lower = query.lowercase()
        val isQuestionOrGuide = homaiQuestionKeywords.any { lower.contains(it) }
        if (isQuestionOrGuide) return emptyList()

        val rawWords = lower.replace(Regex("[^\\p{L}0-9\\s]"), " ").split("\\s+".toRegex())
        return rawWords.filter { word -> word.length >= 2 && !homaiStopWords.contains(word) }
    }

    private fun searchChannelsForHomai(query: String): List<IptvChannel> {
        val state = _channelsState.value
        if (state !is UiState.Success || state.data.isEmpty()) return emptyList()
        val terms = extractSearchTerms(query)
        if (terms.isEmpty()) return emptyList()
        return state.data.filter { ch ->
            val name = ch.name.lowercase()
            val group = ch.group.lowercase()
            terms.any { term -> name.contains(term) || (term.length >= 4 && group.contains(term)) }
        }.take(3)
    }

    private fun searchMediaForHomai(query: String): List<MediaItem> {
        val state = _mediaState.value
        if (state !is UiState.Success || state.data.isEmpty()) return emptyList()
        val terms = extractSearchTerms(query)
        if (terms.isEmpty()) return emptyList()
        return state.data.filter { item ->
            val title = item.title.lowercase()
            val cat = item.category.lowercase()
            terms.any { term -> title.contains(term) || (term.length >= 4 && cat.contains(term)) }
        }.take(3)
    }

    fun sendHomaiMessage(userText: String) {
        if (userText.isBlank() || _isHomaiThinking.value) return
        val text = userText.trim()
        val userMsg = HomaiMessage(isUser = true, text = text)
        val currentList = _homaiMessages.value.toMutableList()
        currentList.add(userMsg)
        _homaiMessages.value = currentList
        _isHomaiThinking.value = true

        val matchedChannels = searchChannelsForHomai(text)
        val matchedMedia = searchMediaForHomai(text)

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val result = homaiRepository.sendMessage(
                history = currentList,
                userPrompt = text
            )
            val elapsed = System.currentTimeMillis() - startTime
            val minThinkingTime = 1200L
            if (elapsed < minThinkingTime) {
                kotlinx.coroutines.delay(minThinkingTime - elapsed)
            }
            _isHomaiThinking.value = false
            result.onSuccess { replyText ->
                val homaiMsg = HomaiMessage(
                    isUser = false,
                    text = replyText,
                    suggestedChannels = matchedChannels,
                    suggestedMediaItems = matchedMedia
                )
                _homaiMessages.value = _homaiMessages.value + homaiMsg
            }.onFailure { error ->
                val errorMsg = HomaiMessage(
                    isUser = false,
                    text = "⚠️ Communication issue: ${error.localizedMessage ?: "Could not connect to Homai."}\n\nPlease check your internet connection.",
                    isError = true,
                    suggestedChannels = matchedChannels,
                    suggestedMediaItems = matchedMedia
                )
                _homaiMessages.value = _homaiMessages.value + errorMsg
            }
        }
    }

    fun clearHomaiHistory() {
        _homaiMessages.value = listOf(
            HomaiMessage(
                isUser = false,
                text = "👋 Chat history cleared. How can Homai help you now?"
            )
        )
    }
}
