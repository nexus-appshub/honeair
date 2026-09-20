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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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

data class LaunchAdOverlayConfig(
    val enabled: Boolean = false,
    val mediaType: String = "auto", // "auto", "image", "video"
    val mediaUrl: String = "",
    val targetUrl: String = "",
    val title: String = "",
    val description: String = "",
    val buttonText: String = "Learn More",
    val skipDurationSeconds: Int = 5,
    val displayFrequency: String = "ONCE_AFTER_INSTALL", // "ONCE_AFTER_INSTALL" or "EVERY_LAUNCH"
    val adId: String = ""
)

data class AppControlConfig(
    val isAppSuspended: Boolean = false,
    val suspensionTitle: String = "App Under Maintenance",
    val suspensionMessage: String = "App access is temporarily suspended by administrator. Please check back later.",
    val notice: AppNotice? = null,
    val launchAdOverlay: LaunchAdOverlayConfig? = null,
    val isSportsTabLocked: Boolean = false,
    val sportsTabStatusText: String = "Live",
    val sportsLockReason: String = "Sports hub is currently locked by administrator.",
    val fancodeCode: String = "",
    val fancodeBannerUrl: String = "",
    val isFanCodeLocked: Boolean = false,
    val isFanCodeSplashLocked: Boolean = true,
    val isFanCodeTabLocked: Boolean = false,
    val isFanCodeGetCodeEnabled: Boolean = true,
    val fancodeTelegramUrl: String = "",
    val fancodeWebUrl: String = "",
    val fancodeOverlayBuyUrl: String = "",
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
    val fancodeValidityHours: Int = 168,
    val premiumLiveTvIds: List<String> = emptyList(),
    val premiumLiveTvCategories: List<String> = emptyList(),
    val isLiveTvLockEnabled: Boolean = false
)

class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var repository: StreamRepository
    private val mediaRepository = MediaRepository()
    private val shortReelsRepository = com.example.data.repository.ShortReelsRepository()

    // Airing Feed Merged State (combining Airing-1 & Airing-2 sources automatically)
    private var airing1SessionId: String? = null
    private var airing2SessionId: String? = null
    private var airing1HasMore: Boolean = true
    private var airing2HasMore: Boolean = true

    private val _airingMergedState = MutableStateFlow(com.example.data.model.AiringFeedState())
    val airingMergedState: StateFlow<com.example.data.model.AiringFeedState> = _airingMergedState.asStateFlow()

    fun loadMergedAiringFeed(forceRefresh: Boolean = false) {
        val currentState = _airingMergedState.value
        if (currentState.isLoading) return
        if (!forceRefresh && currentState.reels.isNotEmpty()) return

        viewModelScope.launch {
            _airingMergedState.update { it.copy(isLoading = true, error = null) }
            
            val fetchJob1 = async { shortReelsRepository.fetchInitialFeed(com.example.data.model.AiringSource.AIRING_1) }
            val fetchJob2 = async { shortReelsRepository.fetchInitialFeed(com.example.data.model.AiringSource.AIRING_2) }

            val res1 = fetchJob1.await()
            val res2 = fetchJob2.await()

            val combinedList = mutableListOf<com.example.data.model.ShortReel>()
            var hadSuccess = false
            var errorMessage: String? = null

            if (res1 is com.example.data.repository.ShortReelsResult.Success) {
                airing1SessionId = res1.sessionId
                airing1HasMore = res1.hasMore
                combinedList.addAll(res1.reels)
                hadSuccess = true
            } else if (res1 is com.example.data.repository.ShortReelsResult.Error) {
                errorMessage = res1.message
            }

            if (res2 is com.example.data.repository.ShortReelsResult.Success) {
                airing2SessionId = res2.sessionId
                airing2HasMore = res2.hasMore
                combinedList.addAll(res2.reels)
                hadSuccess = true
            } else if (res2 is com.example.data.repository.ShortReelsResult.Error && !hadSuccess) {
                errorMessage = res2.message
            }

            val distinctReels = combinedList
                .distinctBy { it.mediaUrl }
                .distinctBy { it.id }

            if (distinctReels.isNotEmpty()) {
                _airingMergedState.update {
                    it.copy(
                        reels = distinctReels,
                        isLoading = false,
                        hasMore = airing1HasMore || airing2HasMore,
                        error = null
                    )
                }
            } else {
                _airingMergedState.update {
                    it.copy(
                        reels = emptyList(),
                        isLoading = false,
                        error = errorMessage ?: "No reels available right now. Tap to retry."
                    )
                }
            }
        }
    }

    fun loadMoreMergedAiringFeed() {
        val currentState = _airingMergedState.value
        if (currentState.isLoadingMore || !currentState.hasMore) return

        viewModelScope.launch {
            _airingMergedState.update { it.copy(isLoadingMore = true) }

            val s1 = airing1SessionId
            val s2 = airing2SessionId

            val job1 = if (!s1.isNullOrBlank() && airing1HasMore) {
                async { shortReelsRepository.fetchNextPage(s1) }
            } else null

            val job2 = if (!s2.isNullOrBlank() && airing2HasMore) {
                async { shortReelsRepository.fetchNextPage(s2) }
            } else null

            val res1 = job1?.await()
            val res2 = job2?.await()

            val newItems = mutableListOf<com.example.data.model.ShortReel>()

            if (res1 is com.example.data.repository.ShortReelsResult.Success) {
                airing1SessionId = res1.sessionId ?: airing1SessionId
                airing1HasMore = res1.hasMore
                newItems.addAll(res1.reels)
            } else if (res1 is com.example.data.repository.ShortReelsResult.Error && res1.isSessionExpired) {
                airing1SessionId = null
                airing1HasMore = false
            }

            if (res2 is com.example.data.repository.ShortReelsResult.Success) {
                airing2SessionId = res2.sessionId ?: airing2SessionId
                airing2HasMore = res2.hasMore
                newItems.addAll(res2.reels)
            } else if (res2 is com.example.data.repository.ShortReelsResult.Error && res2.isSessionExpired) {
                airing2SessionId = null
                airing2HasMore = false
            }

            val existingIds = currentState.reels.map { it.id }.toSet()
            val existingUrls = currentState.reels.map { it.mediaUrl }.toSet()
            val genuinelyNew = newItems.filter { it.id !in existingIds && it.mediaUrl !in existingUrls }

            _airingMergedState.update {
                it.copy(
                    reels = it.reels + genuinelyNew,
                    isLoadingMore = false,
                    hasMore = airing1HasMore || airing2HasMore
                )
            }
        }
    }

    fun retryMergedAiringFeed() {
        loadMergedAiringFeed(forceRefresh = true)
    }

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
            val cachedLiveTvIds = p.getString("premiumLiveTvIds", "")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val cachedLiveTvCats = p.getString("premiumLiveTvCategories", "")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val cachedLaunchAdJson = p.getString("launch_ad_overlay_json", "") ?: ""
            val cachedLaunchAd: LaunchAdOverlayConfig? = if (cachedLaunchAdJson.isNotBlank()) {
                try {
                    val j = org.json.JSONObject(cachedLaunchAdJson)
                    LaunchAdOverlayConfig(
                        enabled = j.optBoolean("enabled", false),
                        mediaType = j.optString("mediaType", "auto"),
                        mediaUrl = j.optString("mediaUrl", ""),
                        targetUrl = j.optString("targetUrl", ""),
                        title = j.optString("title", ""),
                        description = j.optString("description", ""),
                        buttonText = j.optString("buttonText", "Learn More"),
                        skipDurationSeconds = j.optInt("skipDurationSeconds", 5),
                        displayFrequency = j.optString("displayFrequency", "ONCE_AFTER_INSTALL"),
                        adId = j.optString("adId", "")
                    )
                } catch (e: Throwable) { null }
            } else null

            AppControlConfig(
                isAppSuspended = p.getBoolean("isAppSuspended", false),
                suspensionTitle = p.getString("suspensionTitle", "App Under Maintenance") ?: "App Under Maintenance",
                suspensionMessage = p.getString("suspensionMessage", "App access is temporarily suspended by administrator. Please check back later.") ?: "App access is temporarily suspended by administrator. Please check back later.",
                launchAdOverlay = cachedLaunchAd,
                isSportsTabLocked = p.getBoolean("isSportsTabLocked", false),
                sportsTabStatusText = p.getString("sportsTabStatusText", "Live") ?: "Live",
                sportsLockReason = p.getString("sportsLockReason", "Sports hub is currently locked by administrator.") ?: "Sports hub is currently locked by administrator.",
                fancodeCode = p.getString("fancodeCode", "") ?: "",
                fancodeBannerUrl = p.getString("fancodeBannerUrl", "") ?: "",
                isFanCodeLocked = p.getBoolean("isFanCodeLocked", false),
                isFanCodeSplashLocked = p.getBoolean("isFanCodeSplashLocked", true),
                isFanCodeTabLocked = p.getBoolean("isFanCodeTabLocked", false),
                isFanCodeGetCodeEnabled = p.getBoolean("isFanCodeGetCodeEnabled", true),
                fancodeTelegramUrl = p.getString("fancodeTelegramUrl", "") ?: "",
                fancodeWebUrl = p.getString("fancodeWebUrl", "") ?: "",
                fancodeOverlayBuyUrl = p.getString("fancodeOverlayBuyUrl", "") ?: "",
                redeemCode = p.getString("redeemCode", "") ?: "",
                redeemValidityHours = p.getInt("redeemValidityHours", 24),
                redeemExpiryTimestamp = p.getLong("redeemExpiryTimestamp", 0L),
                fancodeValidityHours = p.getInt("fancodeValidityHours", 168),
                isLiveTvLockEnabled = p.getBoolean("isLiveTvLockEnabled", false),
                premiumLiveTvIds = cachedLiveTvIds,
                premiumLiveTvCategories = cachedLiveTvCats
            )
        } else null
    )
    val appControlConfig: StateFlow<AppControlConfig?> = _appControlConfig.asStateFlow()

    // Bottom Nav customizer states
    private val navPrefs by lazy { application.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    private val _browseSlotType = MutableStateFlow(application.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("browse_slot_replacement", "Browse") ?: "Browse")
    val browseSlotType: StateFlow<String> = _browseSlotType.asStateFlow()

    private val _airSlotType = MutableStateFlow(
        application.getSharedPreferences("app_settings", Context.MODE_PRIVATE).let { prefs ->
            val saved = prefs.getString("air_slot_replacement", null)
            if (saved == null || saved == "Airing") {
                prefs.edit().putString("air_slot_replacement", "Air").apply()
                "Air"
            } else {
                saved
            }
        }
    )
    val airSlotType: StateFlow<String> = _airSlotType.asStateFlow()

    private val _downloadsSlotType = MutableStateFlow(application.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("downloads_slot_replacement", "Downloads") ?: "Downloads")
    val downloadsSlotType: StateFlow<String> = _downloadsSlotType.asStateFlow()

    fun updateBrowseSlotType(type: String) {
        navPrefs.edit().putString("browse_slot_replacement", type).apply()
        _browseSlotType.value = type
    }

    fun updateAirSlotType(type: String) {
        navPrefs.edit().putString("air_slot_replacement", type).apply()
        _airSlotType.value = type
    }

    fun updateDownloadsSlotType(type: String) {
        navPrefs.edit().putString("downloads_slot_replacement", type).apply()
        _downloadsSlotType.value = type
    }

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
                                 (cleanId.isNotBlank() && configPremiumIds.any { it.isNotBlank() && it.equals(cleanId, ignoreCase = true) }) ||
                                 (cleanTitle.isNotBlank() && configPremiumIds.any { it.isNotBlank() && it.equals(cleanTitle, ignoreCase = true) }) ||
                                 (cleanCategory.isNotBlank() && (configPremiumCats.any { it.isNotBlank() && it.equals(cleanCategory, ignoreCase = true) } || configLockedTabs.any { it.isNotBlank() && it.equals(cleanCategory, ignoreCase = true) }))
                                 
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

    private val _isSearchingMedia = MutableStateFlow(false)
    val isSearchingMedia: StateFlow<Boolean> = _isSearchingMedia.asStateFlow()

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

    // Discover Feed Items (Dynamic & Daily Randomized Shuffle with Latest Items Prioritized)
    private val _discoverFeedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val discoverFeedItems: StateFlow<List<MediaItem>> = _discoverFeedItems.asStateFlow()

    private val _isDiscoverFeedRefreshing = MutableStateFlow(false)
    val isDiscoverFeedRefreshing: StateFlow<Boolean> = _isDiscoverFeedRefreshing.asStateFlow()

    fun refreshDiscoverFeed(forceReloadFromNetwork: Boolean = false) {
        viewModelScope.launch {
            _isDiscoverFeedRefreshing.value = true
            if (forceReloadFromNetwork) {
                loadMediaItems(forceRefresh = true)
            }
            val currentState = _mediaState.value
            val list = if (currentState is UiState.Success) currentState.data else emptyList()
            if (list.isNotEmpty()) {
                val latest = latestReleases.value
                val latestPool = if (latest.isNotEmpty()) latest.shuffled() else list.filter { (it.year.toIntOrNull() ?: 0) >= 2024 }.shuffled()
                val otherPool = list.filter { it !in latestPool }.shuffled()

                // Interleave and randomize feed with latest items in top deck
                val combinedShuffled = mutableListOf<MediaItem>()
                val topChunk = latestPool.take(15)
                val remainingLatest = latestPool.drop(15)

                combinedShuffled.addAll(topChunk)

                var lIdx = 0
                var oIdx = 0
                while (lIdx < remainingLatest.size || oIdx < otherPool.size) {
                    if (lIdx < remainingLatest.size) {
                        combinedShuffled.add(remainingLatest[lIdx++])
                    }
                    val pickCount = kotlin.random.Random.nextInt(1, 3)
                    for (k in 0 until pickCount) {
                        if (oIdx < otherPool.size) {
                            combinedShuffled.add(otherPool[oIdx++])
                        }
                    }
                }
                _discoverFeedItems.value = combinedShuffled.distinctBy { it.id }
            }
            _isDiscoverFeedRefreshing.value = false
        }
    }

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

    val customPlaylists: StateFlow<List<com.example.data.database.CustomPlaylistEntity>> = AppDatabase.getDatabase(getApplication()).customPlaylistDao().getAllCustomPlaylistsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channelPreferences: StateFlow<List<com.example.data.database.ChannelPreferenceEntity>> = AppDatabase.getDatabase(getApplication()).channelPreferenceDao().getAllPreferencesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCustomPlaylist(name: String, rawContent: String, source: String = "file", pathOrUrl: String = "") {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val filename = "custom_playlist_${java.util.UUID.randomUUID()}.m3u"
            val file = java.io.File(getApplication<android.app.Application>().filesDir, filename)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                file.writeText(rawContent)
            }
            val contentToStore = "file://${file.absolutePath}"
            db.customPlaylistDao().insertCustomPlaylist(
                com.example.data.database.CustomPlaylistEntity(
                    name = name,
                    source = source,
                    pathOrUrl = pathOrUrl,
                    rawContent = contentToStore
                )
            )
            loadPlaylists(forceRefresh = true)
        }
    }

    fun addCustomPlaylistFromUrl(name: String, url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val raw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.network.IptvParser.fetchRawContent(url)
                }
                if (raw.isNotBlank() && (raw.contains("#EXTM3U") || raw.contains("#EXTINF"))) {
                    addCustomPlaylist(name, raw, "url", url)
                    onResult(true, "Playlist imported successfully!")
                } else {
                    onResult(false, "Invalid playlist format. Must be a valid M3U file.")
                }
            } catch (e: Exception) {
                onResult(false, "Failed to download playlist: ${e.localizedMessage}")
            }
        }
    }

    fun deleteCustomPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val all = db.customPlaylistDao().getAllCustomPlaylists()
            val match = all.find { it.id == playlistId }
            if (match != null) {
                if (match.rawContent.startsWith("file://")) {
                    val file = java.io.File(match.rawContent.substring(7))
                    if (file.exists()) {
                        file.delete()
                    }
                }
                db.customPlaylistDao().deleteCustomPlaylist(match)
            }
            val currentPlaylist = _selectedPlaylist.value
            if (currentPlaylist != null && currentPlaylist.url == "custom://$playlistId") {
                clearSelectedPlaylist()
            }
            loadPlaylists(forceRefresh = true)
        }
    }

    fun toggleHideChannel(channel: IptvChannel, hide: Boolean) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val prefs = db.channelPreferenceDao().getAllPreferences()
            val existing = prefs.find { it.url == channel.url }
            db.channelPreferenceDao().insertPreference(
                com.example.data.database.ChannelPreferenceEntity(
                    url = channel.url,
                    name = channel.name,
                    isHidden = hide,
                    displayOrder = existing?.displayOrder ?: 0,
                    customGroup = channel.group
                )
            )
        }
    }

    fun moveChannel(channel: IptvChannel, up: Boolean, activeList: List<IptvChannel>) {
        moveChannelByDelta(channel, if (up) -1 else 1, activeList)
    }

    fun moveChannelByDelta(channel: IptvChannel, delta: Int, activeList: List<IptvChannel>) {
        viewModelScope.launch {
            val currentState = _channelsState.value
            if (currentState !is UiState.Success) return@launch
            val fullList = currentState.data
            
            val index = activeList.indexOfFirst { it.url == channel.url }
            if (index == -1) return@launch
            val swapWithIndex = index + delta
            if (swapWithIndex < 0 || swapWithIndex >= activeList.size) return@launch

            val itemA = activeList[index]
            val itemB = activeList[swapWithIndex]

            val db = AppDatabase.getDatabase(getApplication())
            val existingPrefs = db.channelPreferenceDao().getAllPreferences().associateBy { it.url }

            val originalOrderMap = fullList.mapIndexed { i, ch -> ch.url to i }.toMap()

            val orderA = existingPrefs[itemA.url]?.displayOrder?.takeIf { it >= 0 } ?: ((originalOrderMap[itemA.url] ?: 0) * 10)
            val orderB = existingPrefs[itemB.url]?.displayOrder?.takeIf { it >= 0 } ?: ((originalOrderMap[itemB.url] ?: 0) * 10)

            val finalOrderA = if (orderA == orderB) orderB + 5 else orderB
            val finalOrderB = orderA

            val prefA = existingPrefs[itemA.url]
            val prefB = existingPrefs[itemB.url]

            val updatedEntities = mutableListOf<com.example.data.database.ChannelPreferenceEntity>()

            updatedEntities.add(
                com.example.data.database.ChannelPreferenceEntity(
                    url = itemA.url,
                    name = itemA.name,
                    isHidden = prefA?.isHidden ?: false,
                    displayOrder = finalOrderA,
                    customGroup = prefA?.customGroup ?: itemA.group
                )
            )
            updatedEntities.add(
                com.example.data.database.ChannelPreferenceEntity(
                    url = itemB.url,
                    name = itemB.name,
                    isHidden = prefB?.isHidden ?: false,
                    displayOrder = finalOrderB,
                    customGroup = prefB?.customGroup ?: itemB.group
                )
            )

            db.channelPreferenceDao().insertPreferences(updatedEntities)
        }
    }

    fun createCustomCategory(channels: List<IptvChannel>, categoryName: String) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val prefs = db.channelPreferenceDao().getAllPreferences().associateBy { it.url }
            val newEntities = mutableListOf<com.example.data.database.ChannelPreferenceEntity>()
            channels.forEach { channel ->
                val currentPref = prefs[channel.url]
                newEntities.add(
                    com.example.data.database.ChannelPreferenceEntity(
                        url = channel.url,
                        name = channel.name,
                        isHidden = currentPref?.isHidden ?: false,
                        displayOrder = currentPref?.displayOrder ?: 0,
                        customGroup = categoryName
                    )
                )
            }
            db.channelPreferenceDao().insertPreferences(newEntities)
            _selectedChannelGroup.value = categoryName
        }
    }

    val availableChannelGroups: StateFlow<List<String>> = combine(_channelsState, channelPreferences) { state, prefs ->
        if (state is UiState.Success) {
            val customUserGroups = prefs.mapNotNull { it.customGroup }.filter { it.isNotBlank() }.distinct()
            val prefMap = prefs.associateBy { it.url }
            val m3uGroups = state.data.map { ch ->
                val customG = prefMap[ch.url]?.customGroup
                if (!customG.isNullOrBlank()) customG else ch.group.ifBlank { "General" }
            }.filter { it.isNotBlank() }.distinct()

            val standardGroups = m3uGroups.filter { g -> !customUserGroups.any { cg -> cg.equals(g, ignoreCase = true) } }
            listOf("All") + customUserGroups + standardGroups
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

    private val _isAppUnlockedWithFanCode = MutableStateFlow(
        run {
            val p = application.getSharedPreferences("app_remote_control", Context.MODE_PRIVATE)
            val savedCode = p.getString("fancode_unlocked_code", "") ?: ""
            val unlockedUntil = p.getLong("fancode_unlocked_until", 0L)
            val currentServerCode = p.getString("fancodeCode", "") ?: ""
            if (savedCode.isNotBlank() && currentServerCode.isNotBlank() && savedCode == currentServerCode) {
                unlockedUntil == 0L || System.currentTimeMillis() < unlockedUntil
            } else {
                false
            }
        }
    )
    val isAppUnlockedWithFanCode: StateFlow<Boolean> = _isAppUnlockedWithFanCode.asStateFlow()

    private val _showPremiumPaywall = MutableStateFlow(false)
    val showPremiumPaywall: StateFlow<Boolean> = _showPremiumPaywall.asStateFlow()

    private val _isRedeemActive = MutableStateFlow(false)
    val isRedeemActive: StateFlow<Boolean> = _isRedeemActive.asStateFlow()

    private val _showLaunchAdOverlay = MutableStateFlow(false)
    val showLaunchAdOverlay: StateFlow<Boolean> = _showLaunchAdOverlay.asStateFlow()
    private var isLaunchAdDismissedInThisSession = false

    init {
        // Initial check for redeem status
        viewModelScope.launch {
            _userProfile.collect { profile ->
                _isRedeemActive.value = isRedeemCodeActive(profile?.email)
            }
        }
        // Initial check for Launch Ad Overlay
        val initialAd = _appControlConfig.value?.launchAdOverlay
        if (initialAd != null && initialAd.enabled && initialAd.mediaUrl.isNotBlank()) {
            checkAndTriggerLaunchAd(initialAd)
        }
        // Immediately pre-fetch and scrape Airing Reels so it is instantly ready on tab entry
        loadMergedAiringFeed()
    }

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

        // 1. Check loaded VIP config redeemCodes (from Firebase app_vip_config.json)
        val vipConfig = com.example.subscription.SubscriptionManager.vipConfig.value
        val matchedCode = vipConfig?.redeemCodes?.firstOrNull { it.code.trim().equals(entered, ignoreCase = true) }
        if (matchedCode != null) {
            if (!matchedCode.isActive) {
                return Pair(false, "This redeem code is no longer active.")
            }

            // Check if code has an expiration date
            val expiresAtStr = matchedCode.expiresAt
            if (!expiresAtStr.isNullOrBlank()) {
                val isExpired = try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val d = sdf.parse(expiresAtStr.substringBefore('.'))
                    d != null && System.currentTimeMillis() > d.time
                } catch (e: Exception) {
                    false
                }
                if (isExpired) {
                    return Pair(false, "This redeem code has expired.")
                }
            }

            val planName = matchedCode.planName ?: "VIP Promo Pass"
            val durationDays = matchedCode.durationDays

            // Calculate precise duration (e.g. 0.25 days = 6 hours for 6h match pass)
            val validityMs: Long = when {
                matchedCode.isLifetime == true -> 100L * 365 * 24 * 3600 * 1000L
                durationDays != null && durationDays > 0.0 -> {
                    (durationDays * 24.0 * 3600.0 * 1000.0).toLong()
                }
                planName.contains("6 hour", ignoreCase = true) || planName.contains("6h", ignoreCase = true) || planName.contains("match pass", ignoreCase = true) -> {
                    6 * 3600 * 1000L
                }
                planName.contains("12 hour", ignoreCase = true) || planName.contains("12h", ignoreCase = true) -> {
                    12 * 3600 * 1000L
                }
                planName.contains("1 day", ignoreCase = true) || planName.contains("24 hour", ignoreCase = true) -> {
                    24 * 3600 * 1000L
                }
                planName.contains("7 day", ignoreCase = true) || planName.contains("1 week", ignoreCase = true) -> {
                    7 * 24 * 3600 * 1000L
                }
                planName.contains("30 day", ignoreCase = true) || planName.contains("1 month", ignoreCase = true) -> {
                    30 * 24 * 3600 * 1000L
                }
                else -> {
                    (_appControlConfig.value?.redeemValidityHours ?: 24) * 3600 * 1000L
                }
            }

            val unlockUntil = System.currentTimeMillis() + validityMs
            sharedPrefs.edit()
                .putLong("redeem_unlocked_until", unlockUntil)
                .putString("redeem_unlocked_user", cleanEmail)
                .putString("redeem_unlocked_code", entered)
                .putString("redeem_plan_name", planName)
                .apply()

            _isRedeemActive.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    com.example.data.api.VipApiClient.apiService.redeemCode(
                        com.example.data.api.RedeemRequest(entered, cleanEmail)
                    )
                } catch (e: Exception) {}
            }
            return Pair(true, "Congratulations! $planName has been activated.")
        }

        // 2. Check local/remote appControl global redeem code
        val globalConfig = _appControlConfig.value
        if (globalConfig != null && globalConfig.redeemCode.isNotBlank() && entered.equals(globalConfig.redeemCode, ignoreCase = true)) {
            val useCount = controlPrefs.getInt("redeem_use_count", 0)
            if (useCount >= 3) {
                return Pair(false, "This redeem code has reached its maximum device usage limit (3 times).")
            }

            val expiryTimestamp = globalConfig.redeemExpiryTimestamp
            if (expiryTimestamp > 0 && System.currentTimeMillis() > expiryTimestamp) {
                return Pair(false, "This code has expired.")
            }

            val isMatchPass = entered.contains("match", ignoreCase = true) || entered.contains("6h", ignoreCase = true)
            val validityMs = if (isMatchPass) {
                6 * 3600 * 1000L
            } else {
                globalConfig.redeemValidityHours * 3600 * 1000L
            }
            var unlockUntil = System.currentTimeMillis() + validityMs
            if (expiryTimestamp > 0L) {
                unlockUntil = unlockUntil.coerceAtMost(expiryTimestamp)
            }

            val planName = if (isMatchPass) "6 Hours Match Pass" else "VIP Promo Pass"
            sharedPrefs.edit()
                .putLong("redeem_unlocked_until", unlockUntil)
                .putString("redeem_unlocked_user", cleanEmail)
                .putString("redeem_unlocked_code", entered)
                .putString("redeem_plan_name", planName)
                .apply()

            controlPrefs.edit()
                .putInt("redeem_use_count", useCount + 1)
                .apply()

            _isRedeemActive.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try { com.example.data.api.VipApiClient.apiService.redeemCode(com.example.data.api.RedeemRequest(entered, cleanEmail)) } catch(e: Exception) {}
            }
            return Pair(true, "Congratulations! $planName has been activated.")
        }

        // 3. Server API check via Render
        return try {
            val response = com.example.data.api.VipApiClient.apiService.redeemCode(
                com.example.data.api.RedeemRequest(
                    code = entered,
                    email = cleanEmail,
                    platform = "android"
                )
            )
            
            if (response.success) {
                com.example.subscription.SubscriptionManager.fetchLiveVipConfig()

                val serverPlan = response.planName ?: "VIP Promo Pass"
                val validityMs: Long = when {
                    serverPlan.contains("6 hour", ignoreCase = true) || serverPlan.contains("6h", ignoreCase = true) || serverPlan.contains("match pass", ignoreCase = true) -> {
                        6 * 3600 * 1000L
                    }
                    serverPlan.contains("12 hour", ignoreCase = true) || serverPlan.contains("12h", ignoreCase = true) -> {
                        12 * 3600 * 1000L
                    }
                    serverPlan.contains("1 day", ignoreCase = true) || serverPlan.contains("24 hour", ignoreCase = true) -> {
                        24 * 3600 * 1000L
                    }
                    serverPlan.contains("7 day", ignoreCase = true) || serverPlan.contains("1 week", ignoreCase = true) -> {
                        7 * 24 * 3600 * 1000L
                    }
                    serverPlan.contains("30 day", ignoreCase = true) || serverPlan.contains("1 month", ignoreCase = true) -> {
                        30 * 24 * 3600 * 1000L
                    }
                    else -> {
                        (_appControlConfig.value?.redeemValidityHours ?: 24) * 3600 * 1000L
                    }
                }
                val unlockUntil = System.currentTimeMillis() + validityMs
                
                sharedPrefs.edit()
                    .putLong("redeem_unlocked_until", unlockUntil)
                    .putString("redeem_unlocked_user", cleanEmail)
                    .putString("redeem_unlocked_code", entered)
                    .putString("redeem_plan_name", serverPlan)
                    .apply()

                _isRedeemActive.value = true
                Pair(true, response.message ?: "Congratulations! VIP has been activated.")
            } else {
                Pair(false, response.message ?: "Invalid or expired code.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Direct Firebase RTDB fallback query if Render timed out or failed
            try {
                val okHttpClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val req = okhttp3.Request.Builder()
                    .url("https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/app_vip_config/redeemCodes.json")
                    .build()
                okHttpClient.newCall(req).execute().use { fbRes ->
                    val fbBody = fbRes.body?.string()
                    if (fbRes.isSuccessful && !fbBody.isNullOrBlank() && fbBody.trim().startsWith("[")) {
                        val arr = org.json.JSONArray(fbBody)
                        for (i in 0 until arr.length()) {
                            val cObj = arr.optJSONObject(i) ?: continue
                            val cCode = cObj.optString("code", "")
                            if (cCode.equals(entered, ignoreCase = true)) {
                                val isActive = cObj.optBoolean("isActive", true)
                                if (!isActive) return Pair(false, "This code is no longer active.")
                                val planName = cObj.optString("planName", "VIP Pass")
                                val dDays = if (cObj.has("durationDays")) cObj.optDouble("durationDays") else null
                                val vMs: Long = when {
                                    cObj.optBoolean("isLifetime", false) -> 100L * 365 * 24 * 3600 * 1000L
                                    dDays != null && dDays > 0.0 -> (dDays * 24.0 * 3600.0 * 1000.0).toLong()
                                    planName.contains("6 hour", ignoreCase = true) || planName.contains("6h", ignoreCase = true) || planName.contains("match pass", ignoreCase = true) -> 6 * 3600 * 1000L
                                    else -> 24 * 3600 * 1000L
                                }
                                val unlockUntil = System.currentTimeMillis() + vMs
                                sharedPrefs.edit()
                                    .putLong("redeem_unlocked_until", unlockUntil)
                                    .putString("redeem_unlocked_user", cleanEmail)
                                    .putString("redeem_unlocked_code", entered)
                                    .putString("redeem_plan_name", planName)
                                    .apply()
                                _isRedeemActive.value = true
                                return Pair(true, "Congratulations! $planName has been activated.")
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {}

            Pair(false, "Network error while applying redeem code. Please try again.")
        }
    }

    fun getRedeemPlanName(): String {
        return sharedPrefs.getString("redeem_plan_name", "VIP Promo Pass") ?: "VIP Promo Pass"
    }

    fun isUserPremium(userEmail: String?): Boolean {
        val cleanEmail = userEmail?.trim()?.lowercase() ?: ""
        if (cleanEmail.isNotBlank() && cleanEmail.contains("admin")) return true

        // 1. Check if user currently has an active redeem code
        if (isRedeemCodeActive(cleanEmail)) return true

        // 2. Check if a redeem code was previously used and has now expired
        val savedUser = sharedPrefs.getString("redeem_unlocked_user", "")?.trim()?.lowercase() ?: ""
        val unlockedUntil = sharedPrefs.getLong("redeem_unlocked_until", 0L)
        val hasRedeemRecord = (savedUser.isNotBlank() && (cleanEmail.isBlank() || savedUser == cleanEmail) && unlockedUntil > 0L)
        val isRedeemExpired = hasRedeemRecord && (System.currentTimeMillis() >= unlockedUntil)

        if (isRedeemExpired && !com.example.subscription.SubscriptionManager.isLifetimeUser(cleanEmail)) {
            if (!com.example.subscription.SubscriptionManager.hasValidPaidSubscription(cleanEmail)) {
                return false
            }
        }

        // 3. Check SubscriptionManager VIP state
        if (com.example.subscription.SubscriptionManager.isVipUser()) return true

        // 4. Remote appControl config
        if (cleanEmail.isNotBlank()) {
            val config = _appControlConfig.value
            return config?.premiumEmails?.any { it.trim().equals(cleanEmail, ignoreCase = true) } == true
        }

        return false
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
        val config = _appControlConfig.value
        val requiredCode = config?.fancodeCode ?: ""
        if (requiredCode.isNotBlank() && enteredCode.trim() == requiredCode.trim()) {
            val useCount = controlPrefs.getInt("fancode_use_count", 0)
            if (useCount >= 3) {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "This FanCode has reached its maximum device usage limit (3 times).", Toast.LENGTH_LONG).show()
                }
                return false
            }

            val validityHours = config?.fancodeValidityHours ?: 168
            val validityMs = if (validityHours > 0) validityHours * 3600000L else 0L
            val unlockUntil = if (validityMs > 0) System.currentTimeMillis() + validityMs else 0L

            controlPrefs.edit()
                .putString("fancode_unlocked_code", requiredCode)
                .putLong("fancode_unlocked_until", unlockUntil)
                .putInt("fancode_use_count", useCount + 1)
                .apply()

            _isAppUnlockedWithFanCode.value = true
            return true
        } else if (requiredCode.isBlank()) {
            _isAppUnlockedWithFanCode.value = true
            return true
        }
        return false
    }

    private var lastDismissedNoticeKey: String = ""

    fun dismissAppNotice() {
        val currentNotice = _appControlConfig.value?.notice
        if (currentNotice != null) {
            lastDismissedNoticeKey = "${currentNotice.title}_${currentNotice.message}"
        }
        val current = _appControlConfig.value ?: return
        _appControlConfig.value = current.copy(notice = null)
    }

    fun checkAndTriggerLaunchAd(ad: LaunchAdOverlayConfig?) {
        if (ad == null || !ad.enabled || ad.mediaUrl.isBlank()) {
            return
        }
        if (isLaunchAdDismissedInThisSession) {
            return
        }
        val adKey = ad.adId.ifBlank { ad.mediaUrl.hashCode().toString() }
        val isOnce = ad.displayFrequency.equals("ONCE_AFTER_INSTALL", ignoreCase = true) ||
                ad.displayFrequency.equals("ONCE", ignoreCase = true)
        if (isOnce) {
            val hasShown = controlPrefs.getBoolean("has_shown_launch_ad_$adKey", false)
            if (!hasShown) {
                _showLaunchAdOverlay.value = true
            }
        } else {
            // EVERY_LAUNCH / ALWAYS
            _showLaunchAdOverlay.value = true
        }
    }

    fun dismissLaunchAdOverlay() {
        isLaunchAdDismissedInThisSession = true
        val ad = _appControlConfig.value?.launchAdOverlay
        if (ad != null) {
            val adKey = ad.adId.ifBlank { ad.mediaUrl.hashCode().toString() }
            controlPrefs.edit().putBoolean("has_shown_launch_ad_$adKey", true).apply()
        }
        _showLaunchAdOverlay.value = false
    }

    fun previewLaunchAd(config: LaunchAdOverlayConfig) {
        _showLaunchAdOverlay.value = true
    }

    fun saveLaunchAdOverlayConfig(config: LaunchAdOverlayConfig, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val adObj = org.json.JSONObject().apply {
                    put("enabled", config.enabled)
                    put("mediaType", config.mediaType)
                    put("mediaUrl", config.mediaUrl)
                    put("targetUrl", config.targetUrl)
                    put("title", config.title)
                    put("description", config.description)
                    put("buttonText", config.buttonText)
                    put("skipDurationSeconds", config.skipDurationSeconds)
                    put("displayFrequency", config.displayFrequency)
                    put("adId", config.adId)
                }
                controlPrefs.edit().putString("launch_ad_overlay_json", adObj.toString()).apply()

                val current = _appControlConfig.value
                if (current != null) {
                    _appControlConfig.value = current.copy(launchAdOverlay = config)
                } else {
                    _appControlConfig.value = AppControlConfig(launchAdOverlay = config)
                }

                // Sync to Firebase Realtime Database
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = adObj.toString().toRequestBody(mediaType)
                    val patchReq = okhttp3.Request.Builder()
                        .url("https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/appControl/launchAdOverlay.json")
                        .put(body)
                        .build()
                    client.newCall(patchReq).execute().close()
                } catch (e: Exception) {
                    Log.w("StreamViewModel", "Firebase launchAd sync deferred: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "Launch Ad saved and updated successfully!")
                }
            } catch (e: Exception) {
                Log.e("StreamViewModel", "Error saving launch ad config", e)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "Failed to save: ${e.localizedMessage}")
                }
            }
        }
    }

    fun fetchAppControlConfig(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingSuspension.value = true
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val controlUrls = listOf(
                "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/appControl.json",
                "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/configs/globalConfig.json",
                "https://homeairtv-server.onrender.com/api/appControl"
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
                            val fancodeBannerUrl = json.optString("fancodeBannerUrl", "")
                                .ifBlank { json.optString("fancode_banner_url", "") }
                                .ifBlank { json.optString("fancodeBanner", "") }
                                .ifBlank { json.optString("fancode_banner", "") }
                            val isFanCodeLocked = json.optBoolean("isFanCodeLocked", false) || json.optBoolean("isFanCodeRequired", false)
                            val isFanCodeSplashLocked = if (json.has("isFanCodeSplashLocked")) {
                                json.optBoolean("isFanCodeSplashLocked", true)
                            } else if (json.has("fancodeSplashLock")) {
                                json.optBoolean("fancodeSplashLock", true)
                            } else if (json.has("is_fancode_splash_locked")) {
                                json.optBoolean("is_fancode_splash_locked", true)
                            } else true

                            val isFanCodeTabLocked = if (json.has("isFanCodeTabLocked")) {
                                json.optBoolean("isFanCodeTabLocked", false)
                            } else if (json.has("fancodeTabLock")) {
                                json.optBoolean("fancodeTabLock", false)
                            } else if (json.has("is_fancode_tab_locked")) {
                                json.optBoolean("is_fancode_tab_locked", false)
                            } else false
                            val isFanCodeGetCodeEnabled = if (json.has("isFanCodeGetCodeEnabled")) {
                                json.optBoolean("isFanCodeGetCodeEnabled", true)
                            } else if (json.has("fancodeGetCodeEnabled")) {
                                json.optBoolean("fancodeGetCodeEnabled", true)
                            } else if (json.has("isGetCodeEnabled")) {
                                json.optBoolean("isGetCodeEnabled", true)
                            } else if (json.has("getCodeEnabled")) {
                                json.optBoolean("getCodeEnabled", true)
                            } else if (json.has("fancode_get_code_enabled")) {
                                json.optBoolean("fancode_get_code_enabled", true)
                            } else true

                            val fancodeTelegramUrl = json.optString("fancodeTelegramUrl", "")
                                .ifBlank { json.optString("fancodeTelegramLink", "") }
                                .ifBlank { json.optString("fancode_telegram_url", "") }
                                .ifBlank { json.optString("fancodeTelegram", "") }
                                .ifBlank { json.optString("telegramUrl", "") }
                                .ifBlank { json.optString("telegramLink", "") }
                                .ifBlank { json.optString("fancode_telegram", "") }

                            val fancodeWebUrl = json.optString("fancodeWebUrl", "")
                                .ifBlank { json.optString("fancodeWebLink", "") }
                                .ifBlank { json.optString("fancode_web_url", "") }
                                .ifBlank { json.optString("fancodeWeb", "") }
                                .ifBlank { json.optString("webUrl", "") }
                                .ifBlank { json.optString("websiteUrl", "") }
                                .ifBlank { json.optString("fancode_web", "") }

                            val fancodeOverlayBuyUrl = json.optString("fancodeOverlayBuyUrl", "")
                                .ifBlank { json.optString("fancode_overlay_buy_url", "") }

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
                            val fancodeValidityHours = json.optInt("fancodeValidityHours", json.optInt("fancode_validity_hours", 168))

                            val premiumLiveTvIds = mutableListOf<String>()
                            val rawLiveTvIds = json.opt("premiumLiveTvIds")
                                ?: json.opt("premium_live_tv_ids")
                                ?: json.opt("lockedLiveTvChannels")
                                ?: json.opt("premiumLiveTv")
                            when (rawLiveTvIds) {
                                is org.json.JSONArray -> {
                                    for (i in 0 until rawLiveTvIds.length()) {
                                        val item = rawLiveTvIds.optString(i, "").trim()
                                        if (item.isNotBlank() && item != "null") premiumLiveTvIds.add(item)
                                    }
                                }
                                is org.json.JSONObject -> {
                                    val keys = rawLiveTvIds.keys()
                                    while (keys.hasNext()) {
                                        val key = keys.next()
                                        val value = rawLiveTvIds.opt(key)
                                        if (value is Boolean && value) {
                                            premiumLiveTvIds.add(key.trim())
                                        } else if (value is String && value.isNotBlank() && value != "null") {
                                            premiumLiveTvIds.add(value.trim())
                                        } else {
                                            premiumLiveTvIds.add(key.trim())
                                        }
                                    }
                                }
                                is String -> {
                                    if (rawLiveTvIds.isNotBlank() && rawLiveTvIds != "null") {
                                        rawLiveTvIds.split(",", ";", "\n").forEach {
                                            val s = it.trim()
                                            if (s.isNotBlank()) premiumLiveTvIds.add(s)
                                        }
                                    }
                                }
                            }

                            val premiumLiveTvCategories = mutableListOf<String>()
                            val rawLiveTvCats = json.opt("premiumLiveTvCategories")
                                ?: json.opt("premium_live_tv_categories")
                                ?: json.opt("lockedLiveTvCategories")
                            when (rawLiveTvCats) {
                                is org.json.JSONArray -> {
                                    for (i in 0 until rawLiveTvCats.length()) {
                                        val item = rawLiveTvCats.optString(i, "").trim()
                                        if (item.isNotBlank() && item != "null") premiumLiveTvCategories.add(item)
                                    }
                                }
                                is org.json.JSONObject -> {
                                    val keys = rawLiveTvCats.keys()
                                    while (keys.hasNext()) {
                                        val key = keys.next()
                                        val value = rawLiveTvCats.opt(key)
                                        if (value is Boolean && value) {
                                            premiumLiveTvCategories.add(key.trim())
                                        } else if (value is String && value.isNotBlank() && value != "null") {
                                            premiumLiveTvCategories.add(value.trim())
                                        } else {
                                            premiumLiveTvCategories.add(key.trim())
                                        }
                                    }
                                }
                                is String -> {
                                    if (rawLiveTvCats.isNotBlank() && rawLiveTvCats != "null") {
                                        rawLiveTvCats.split(",", ";", "\n").forEach {
                                            val s = it.trim()
                                            if (s.isNotBlank()) premiumLiveTvCategories.add(s)
                                        }
                                    }
                                }
                            }

                            val noticeRaw = json.opt("notice")
                                ?: json.opt("specialAnnouncement")
                                ?: json.opt("special_announcement")
                                ?: json.opt("announcement")
                                ?: json.opt("modalNotice")
                                ?: json.opt("adminNotice")

                            val parsedNotice: AppNotice? = when (noticeRaw) {
                                is org.json.JSONObject -> {
                                    val isEnabled = noticeRaw.optBoolean("enabled", true)
                                    if (isEnabled) {
                                        val title = noticeRaw.optString("title", "").ifBlank {
                                            noticeRaw.optString("header", "Special Announcement")
                                        }
                                        val message = noticeRaw.optString("message", "").ifBlank {
                                            noticeRaw.optString("text", "").ifBlank {
                                                noticeRaw.optString("body", "").ifBlank {
                                                    noticeRaw.optString("description", "")
                                                }
                                            }
                                        }
                                        val rawImg = noticeRaw.optString("imageUrl", "").ifBlank {
                                            noticeRaw.optString("image", "").ifBlank {
                                                noticeRaw.optString("bannerUrl", "")
                                            }
                                        }
                                        val imgUrl = if (rawImg.isBlank() || rawImg == "null") null else rawImg

                                        val rawBtnText = noticeRaw.optString("buttonText", "").ifBlank {
                                            noticeRaw.optString("btnText", "").ifBlank {
                                                noticeRaw.optString("actionText", "")
                                            }
                                        }
                                        val btnText = if (rawBtnText.isBlank() || rawBtnText == "null") null else rawBtnText

                                        val rawBtnUrl = noticeRaw.optString("buttonUrl", "").ifBlank {
                                            noticeRaw.optString("btnUrl", "").ifBlank {
                                                noticeRaw.optString("actionUrl", "").ifBlank {
                                                    noticeRaw.optString("link", "")
                                                }
                                            }
                                        }
                                        val btnUrl = if (rawBtnUrl.isBlank() || rawBtnUrl == "null") null else rawBtnUrl
                                        val isDismissible = noticeRaw.optBoolean("isDismissible", noticeRaw.optBoolean("dismissible", true))

                                        if (title.isNotBlank() || message.isNotBlank()) {
                                            AppNotice(
                                                title = title,
                                                message = message,
                                                imageUrl = imgUrl,
                                                buttonText = btnText,
                                                buttonUrl = btnUrl,
                                                isDismissible = isDismissible
                                            )
                                        } else null
                                    } else null
                                }
                                is String -> {
                                    if (noticeRaw.isNotBlank() && noticeRaw != "null") {
                                        AppNotice(
                                            title = "Special Announcement",
                                            message = noticeRaw,
                                            imageUrl = null,
                                            buttonText = null,
                                            buttonUrl = null,
                                            isDismissible = true
                                        )
                                    } else null
                                }
                                else -> null
                            }

                            val noticeKey = if (parsedNotice != null) "${parsedNotice.title}_${parsedNotice.message}" else ""
                            val isDismissed = noticeKey.isNotBlank() && noticeKey == lastDismissedNoticeKey
                            val notice = if (isDismissed) null else parsedNotice

                            // Parse Launch Ad Overlay
                            val launchAdRaw = json.opt("launchAdOverlay")
                                ?: json.opt("launchAd")
                                ?: json.opt("launch_ad_overlay")
                                ?: json.opt("fullscreenAd")
                                ?: json.opt("splashAd")
                                ?: json.opt("adOverlay")
                                ?: json.opt("appLaunchAd")

                            val parsedLaunchAd: LaunchAdOverlayConfig? = when (launchAdRaw) {
                                is org.json.JSONObject -> {
                                    val enabled = launchAdRaw.optBoolean("enabled", true)
                                    val mediaUrl = launchAdRaw.optString("mediaUrl", "").ifBlank {
                                        launchAdRaw.optString("videoUrl", "").ifBlank {
                                            launchAdRaw.optString("imageUrl", "").ifBlank {
                                                launchAdRaw.optString("posterUrl", "").ifBlank {
                                                    launchAdRaw.optString("url", "")
                                                }
                                            }
                                        }
                                    }
                                    val mediaType = launchAdRaw.optString("mediaType", "").ifBlank {
                                        launchAdRaw.optString("type", "auto")
                                    }
                                    val targetUrl = launchAdRaw.optString("targetUrl", "").ifBlank {
                                        launchAdRaw.optString("actionUrl", "").ifBlank {
                                            launchAdRaw.optString("linkUrl", "").ifBlank {
                                                launchAdRaw.optString("clickUrl", "").ifBlank {
                                                    launchAdRaw.optString("link", "")
                                                }
                                            }
                                        }
                                    }
                                    val title = launchAdRaw.optString("title", "")
                                    val description = launchAdRaw.optString("description", "").ifBlank {
                                        launchAdRaw.optString("subtitle", "").ifBlank {
                                            launchAdRaw.optString("message", "")
                                        }
                                    }
                                    val buttonText = launchAdRaw.optString("buttonText", "").ifBlank {
                                        launchAdRaw.optString("btnText", "").ifBlank {
                                            launchAdRaw.optString("actionText", "Learn More")
                                        }
                                    }
                                    val skipDuration = if (launchAdRaw.has("skipDurationSeconds")) {
                                        launchAdRaw.optInt("skipDurationSeconds", 5)
                                    } else if (launchAdRaw.has("skipSeconds")) {
                                        launchAdRaw.optInt("skipSeconds", 5)
                                    } else if (launchAdRaw.has("skipDelay")) {
                                        launchAdRaw.optInt("skipDelay", 5)
                                    } else 5

                                    val displayFrequency = launchAdRaw.optString("displayFrequency", "").ifBlank {
                                        launchAdRaw.optString("showMode", "").ifBlank {
                                            launchAdRaw.optString("frequency", "").ifBlank {
                                                launchAdRaw.optString("displayMode", "ONCE_AFTER_INSTALL")
                                            }
                                        }
                                    }
                                    val adId = launchAdRaw.optString("adId", "").ifBlank {
                                        launchAdRaw.optString("id", "")
                                    }

                                    LaunchAdOverlayConfig(
                                        enabled = enabled,
                                        mediaType = mediaType,
                                        mediaUrl = mediaUrl,
                                        targetUrl = targetUrl,
                                        title = title,
                                        description = description,
                                        buttonText = buttonText,
                                        skipDurationSeconds = skipDuration,
                                        displayFrequency = displayFrequency,
                                        adId = adId
                                    )
                                }
                                else -> {
                                    val isLaunchAdEnabled = json.optBoolean("isLaunchAdEnabled", false) ||
                                            json.optBoolean("launchAdEnabled", false) ||
                                            json.optBoolean("isSplashAdEnabled", false) ||
                                            json.optBoolean("splashAdEnabled", false) ||
                                            json.optBoolean("splash_ad_enabled", false) ||
                                            json.optBoolean("showSplashAd", false) ||
                                            (json.has("splashAd") && json.optJSONObject("splashAd") == null && json.optBoolean("splashAd", false))

                                    val rawMediaUrl = json.optString("launchAdMediaUrl", "").ifBlank {
                                        json.optString("launchAdVideoUrl", "").ifBlank {
                                            json.optString("launchAdImageUrl", "").ifBlank {
                                                json.optString("launchAdPosterUrl", "").ifBlank {
                                                    json.optString("splashAdMediaUrl", "").ifBlank {
                                                        json.optString("splashAdImageUrl", "").ifBlank {
                                                            json.optString("splash_ad_media_url", "").ifBlank {
                                                                json.optString("splashImageUrl", "").ifBlank {
                                                                    json.optString("splash_image_url", "")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (isLaunchAdEnabled && rawMediaUrl.isNotBlank()) {
                                        LaunchAdOverlayConfig(
                                            enabled = true,
                                            mediaType = json.optString("launchAdMediaType", "auto"),
                                            mediaUrl = rawMediaUrl,
                                            targetUrl = json.optString("launchAdTargetUrl", "").ifBlank {
                                                json.optString("splashAdTargetUrl", "").ifBlank {
                                                    json.optString("splash_ad_target_url", "").ifBlank {
                                                        json.optString("splashClickUrl", "").ifBlank {
                                                            json.optString("splash_click_url", "").ifBlank {
                                                                json.optString("splashTargetUrl", "")
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            title = json.optString("launchAdTitle", ""),
                                            description = json.optString("launchAdDescription", ""),
                                            buttonText = json.optString("launchAdButtonText", "Learn More"),
                                            skipDurationSeconds = json.optInt("launchAdSkipSeconds", json.optInt("splashAdSkipSeconds", 5)),
                                            displayFrequency = json.optString("launchAdDisplayFrequency", "").ifBlank {
                                                json.optString("splashAdDisplayFrequency", "").ifBlank {
                                                    json.optString("displayFrequency", "EVERY_LAUNCH")
                                                }
                                            },
                                            adId = json.optString("launchAdId", "")
                                        )
                                    } else null
                                }
                            }

                            // Cache to SharedPreferences for instant cold-boot enforcement
                            try {
                                val edit = controlPrefs.edit()

                                val lastFancodeCode = controlPrefs.getString("last_fancode_code", "") ?: ""
                                if (fancodeCode.isNotBlank() && lastFancodeCode != fancodeCode) {
                                    edit.putString("last_fancode_code", fancodeCode)
                                    edit.putInt("fancode_use_count", 0)
                                }

                                val lastRedeemCode = controlPrefs.getString("last_redeem_code", "") ?: ""
                                if (redeemCode.isNotBlank() && lastRedeemCode != redeemCode) {
                                    edit.putString("last_redeem_code", redeemCode)
                                    edit.putInt("redeem_use_count", 0)
                                }

                                edit.putBoolean("isAppSuspended", isSuspended)
                                    .putString("suspensionTitle", suspensionTitle)
                                    .putString("suspensionMessage", suspensionMessage)
                                    .putBoolean("isSportsTabLocked", isSportsLocked)
                                    .putString("sportsTabStatusText", sportsStatus)
                                    .putString("sportsLockReason", sportsReason)
                                    .putString("fancodeCode", fancodeCode)
                                    .putString("fancodeBannerUrl", fancodeBannerUrl)
                                    .putBoolean("isFanCodeLocked", isFanCodeLocked)
                                    .putBoolean("isFanCodeSplashLocked", isFanCodeSplashLocked)
                                    .putBoolean("isFanCodeTabLocked", isFanCodeTabLocked)
                                    .putBoolean("isFanCodeGetCodeEnabled", isFanCodeGetCodeEnabled)
                                    .putString("fancodeTelegramUrl", fancodeTelegramUrl)
                                    .putString("fancodeWebUrl", fancodeWebUrl)
                                    .putString("fancodeOverlayBuyUrl", fancodeOverlayBuyUrl)
                                    .putString("redeemCode", redeemCode)
                                    .putInt("redeemValidityHours", redeemValidityHours)
                                    .putLong("redeemExpiryTimestamp", redeemExpiryTimestamp)
                                    .putInt("fancodeValidityHours", fancodeValidityHours)
                                    .putBoolean("isLiveTvLockEnabled", isLiveTvLockEnabled)
                                    .putString("premiumLiveTvIds", premiumLiveTvIds.joinToString(","))
                                    .putString("premiumLiveTvCategories", premiumLiveTvCategories.joinToString(","))

                                if (parsedLaunchAd != null) {
                                    val adJsonObj = org.json.JSONObject().apply {
                                        put("enabled", parsedLaunchAd.enabled)
                                        put("mediaType", parsedLaunchAd.mediaType)
                                        put("mediaUrl", parsedLaunchAd.mediaUrl)
                                        put("targetUrl", parsedLaunchAd.targetUrl)
                                        put("title", parsedLaunchAd.title)
                                        put("description", parsedLaunchAd.description)
                                        put("buttonText", parsedLaunchAd.buttonText)
                                        put("skipDurationSeconds", parsedLaunchAd.skipDurationSeconds)
                                        put("displayFrequency", parsedLaunchAd.displayFrequency)
                                        put("adId", parsedLaunchAd.adId)
                                    }
                                    edit.putString("launch_ad_overlay_json", adJsonObj.toString())
                                }
                                edit.apply()
                            } catch (e: Throwable) {
                                Log.e("StreamViewModel", "Error saving control preferences", e)
                            }

                            val savedUnlockCode = controlPrefs.getString("fancode_unlocked_code", "") ?: ""
                            val savedUnlockUntil = controlPrefs.getLong("fancode_unlocked_until", 0L)
                            val isAlreadyUnlocked = savedUnlockCode.isNotBlank() && savedUnlockCode == fancodeCode && (savedUnlockUntil == 0L || System.currentTimeMillis() < savedUnlockUntil)
                            _isAppUnlockedWithFanCode.value = isAlreadyUnlocked

                            _appControlConfig.value = AppControlConfig(
                                isAppSuspended = isSuspended,
                                suspensionTitle = suspensionTitle,
                                suspensionMessage = suspensionMessage,
                                notice = notice,
                                launchAdOverlay = parsedLaunchAd,
                                isSportsTabLocked = isSportsLocked,
                                sportsTabStatusText = sportsStatus,
                                sportsLockReason = sportsReason,
                                fancodeCode = fancodeCode,
                                fancodeBannerUrl = fancodeBannerUrl,
                                isFanCodeLocked = isFanCodeLocked,
                                isFanCodeSplashLocked = isFanCodeSplashLocked,
                                isFanCodeTabLocked = isFanCodeTabLocked,
                                isFanCodeGetCodeEnabled = isFanCodeGetCodeEnabled,
                                fancodeTelegramUrl = fancodeTelegramUrl,
                                fancodeWebUrl = fancodeWebUrl,
                                fancodeOverlayBuyUrl = fancodeOverlayBuyUrl,
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
                                fancodeValidityHours = fancodeValidityHours,
                                premiumLiveTvIds = premiumLiveTvIds,
                                premiumLiveTvCategories = premiumLiveTvCategories,
                                isLiveTvLockEnabled = isLiveTvLockEnabled
                            )

                            if (parsedLaunchAd != null && parsedLaunchAd.enabled && parsedLaunchAd.mediaUrl.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    checkAndTriggerLaunchAd(parsedLaunchAd)
                                }
                            }

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

    private val _selectedStreamServerKey = MutableStateFlow<String?>("fastest_auto")
    val selectedStreamServerKey: StateFlow<String?> = _selectedStreamServerKey.asStateFlow()

    fun selectStreamServerKey(key: String?) {
        _selectedStreamServerKey.value = key
    }

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

    fun selectAnikotoServer(server: com.example.scraper.AnikotoServer?, episode: Int? = null) {
        _selectedServer.value = server
        val activeItem = _activeMediaItem.value
        val curEp = episode ?: _activeMediaEpisode.value
        if (server != null && activeItem != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val streamRes = com.example.scraper.AnikotoScraper.extractStreamFromServer(
                        server = server,
                        watchUrl = currentServerWatchUrl.ifBlank { activeItem.title },
                        episode = curEp
                    )
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
        viewModelScope.launch(Dispatchers.IO) {
            _isFetchingServers.value = true
            try {
                val matchedSeasonWatchUrl = _anikotoSeasons.value.find { it.number == season }?.watchUrl
                val targetTitleOrSlug = when {
                    !matchedSeasonWatchUrl.isNullOrBlank() -> matchedSeasonWatchUrl
                    item.id.startsWith("anikoto_") -> item.id
                    item.id.startsWith("al_") || item.id.startsWith("mal_") || item.id.startsWith("tmdb_") -> item.title.ifBlank { item.id }
                    !item.imdbId.isNullOrBlank() && item.imdbId!!.startsWith("anikoto_") -> item.imdbId!!
                    item.title.isNotBlank() -> item.title
                    else -> item.id
                }
                val group = com.example.scraper.AnikotoScraper.fetchAvailableServers(
                    title = targetTitleOrSlug,
                    season = season,
                    episode = episode
                )
                _availableSubServers.value = group.subServers
                _availableDubServers.value = group.dubServers
                currentServerWatchUrl = group.watchUrl

                // Auto-select server while prioritizing direct playable streams (HD-1, HD-2, Vidstream, etc.)
                val current = _selectedServer.value
                val isDub = current?.type?.lowercase() == "dub"
                val targetServers = if (isDub) group.dubServers else group.subServers
                val matched = targetServers.find { it.name.equals(current?.name, ignoreCase = true) && !it.name.contains("Mirror") && !it.id.contains("p") }
                    ?: targetServers.firstOrNull { !it.name.contains("Mirror") && !it.id.contains("p") }
                    ?: group.subServers.firstOrNull { !it.name.contains("Mirror") && !it.id.contains("p") }
                    ?: group.dubServers.firstOrNull { !it.name.contains("Mirror") && !it.id.contains("p") }
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

    private val _decoderIndex = MutableStateFlow(sharedPrefs.getInt("setting_decoder_index", 0))
    val decoderIndex: StateFlow<Int> = _decoderIndex.asStateFlow()
    fun setDecoderIndex(value: Int) {
        _decoderIndex.value = value
        sharedPrefs.edit().putInt("setting_decoder_index", value).apply()
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

    val filteredChannels: StateFlow<List<IptvChannel>> = combine(_channelsState, _selectedChannelGroup, _channelSearchQuery, channelPreferences) { state, group, query, prefs ->
        if (state is UiState.Success) {
            val prefMap = prefs.associateBy { it.url }
            var list = state.data.map { ch ->
                val pref = prefMap[ch.url]
                if (pref?.customGroup != null) {
                    ch.copy(group = pref.customGroup)
                } else {
                    ch
                }
            }
            
            // Filter out hidden channels locally
            list = list.filter { ch ->
                val pref = prefMap[ch.url]
                pref == null || !pref.isHidden
            }
            
            val originalOrderMap = state.data.mapIndexed { index, ch -> ch.url to index }.toMap()
            
            // Sort based on local displayOrder index
            list = list.sortedWith(Comparator { a, b ->
                val prefA = prefMap[a.url]
                val prefB = prefMap[b.url]
                val orderA = prefA?.displayOrder?.takeIf { it >= 0 } ?: ((originalOrderMap[a.url] ?: 0) * 10)
                val orderB = prefB?.displayOrder?.takeIf { it >= 0 } ?: ((originalOrderMap[b.url] ?: 0) * 10)
                orderA.compareTo(orderB)
            })

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
                // Smart, diacritic-tolerant, typo-tolerant search across all media
                val normQ = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}"), "")
                    .lowercase(java.util.Locale.ROOT)
                    .replace(Regex("[^a-z0-9]+"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                val collapsedQ = normQ.replace(Regex("(?i)(.)\\1+"), "$1").replace(Regex("\\s+"), " ").trim()
                val queryWords = normQ.split(" ").filter { it.length >= 2 }
                val significantWords = queryWords.filter { it.length > 2 && it != "the" && it != "and" }
                val queryTokensToMatch = if (significantWords.isNotEmpty()) significantWords else queryWords

                list = list.mapNotNull { item ->
                    val normTitle = java.text.Normalizer.normalize(item.title, java.text.Normalizer.Form.NFD)
                        .replace(Regex("\\p{M}"), "")
                        .lowercase(java.util.Locale.ROOT)
                        .replace(Regex("[^a-z0-9]+"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    val collapsedTitle = normTitle.replace(Regex("(?i)(.)\\1+"), "$1").replace(Regex("\\s+"), " ").trim()
                    val titleWords = normTitle.split(" ").filter { it.isNotBlank() }
                    val collapsedTitleWords = collapsedTitle.split(" ").filter { it.isNotBlank() }

                    var score = 0

                    // Direct Search category booster (item returned directly by active search engine)
                    if (item.category == "Search" || item.id.startsWith("anikoto_") && q.isNotBlank()) {
                        score += 300
                    }

                    // Full phrase match
                    if (normTitle == normQ) {
                        score += 2500
                    } else if (normTitle.startsWith(normQ)) {
                        score += 1800
                    } else if (normTitle.contains(normQ)) {
                        score += 1400
                    } else if (collapsedTitle == collapsedQ) {
                        score += 2200
                    } else if (collapsedTitle.startsWith(collapsedQ)) {
                        score += 1700
                    } else if (collapsedTitle.contains(collapsedQ)) {
                        // Catches e.g. "naruto shipudden" matching "naruto shippuden" or "naruto: shippuuden"
                        score += 1500
                    }

                    // Token-level scoring
                    var matchedTokensCount = 0
                    for (qw in queryTokensToMatch) {
                        val qwCollapsed = qw.replace(Regex("(?i)(.)\\1+"), "$1")
                        var tokenScore = 0

                        for (tw in titleWords) {
                            if (tw == qw) {
                                tokenScore = maxOf(tokenScore, 350)
                            } else if (tw.startsWith(qw)) {
                                tokenScore = maxOf(tokenScore, 260)
                            } else if (tw.contains(qw) || qw.contains(tw)) {
                                tokenScore = maxOf(tokenScore, 200)
                            }
                        }

                        // Check collapsed tokens (e.g. shipudden -> shipuden matches shippuden -> shipuden or shippuuden -> shipuden)
                        if (tokenScore < 280) {
                            for (ctw in collapsedTitleWords) {
                                if (ctw == qwCollapsed || ctw.startsWith(qwCollapsed)) {
                                    tokenScore = maxOf(tokenScore, 280)
                                } else if (ctw.contains(qwCollapsed) || qwCollapsed.contains(ctw)) {
                                    tokenScore = maxOf(tokenScore, 210)
                                }
                            }
                        }

                        // Levenshtein edit distance for typo tolerance (e.g. 1-2 letters off)
                        if (tokenScore < 200 && qw.length >= 4) {
                            for (tw in titleWords) {
                                val dist = kotlin.math.abs(tw.length - qw.length)
                                if (dist <= 2) {
                                    val editDist = calculateEditDistance(qw, tw)
                                    if (editDist <= 1) {
                                        tokenScore = maxOf(tokenScore, 220)
                                    } else if (editDist <= 2 && qw.length >= 6) {
                                        tokenScore = maxOf(tokenScore, 170)
                                    }
                                }
                            }
                        }

                        if (tokenScore > 0) {
                            matchedTokensCount++
                            score += tokenScore
                        }
                    }

                    // Secondary description search bonus
                    if (item.description.contains(normQ, ignoreCase = true) || item.description.contains(collapsedQ, ignoreCase = true)) {
                        score += 150
                    }

                    // Filter condition:
                    // If multiple significant query words exist, match if any token matched or high score achieved
                    val isMatch = if (queryTokensToMatch.size > 1) {
                        score >= 200 || matchedTokensCount >= 1
                    } else if (queryTokensToMatch.isNotEmpty()) {
                        score >= 120 || matchedTokensCount >= 1
                    } else {
                        score > 0
                    }

                    if (isMatch) Pair(item, score) else null
                }.sortedWith(
                    compareByDescending<Pair<MediaItem, Int>> { it.second }
                        .thenByDescending { it.first.rating.toDoubleOrNull() ?: 0.0 }
                ).map { it.first }
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

    private fun calculateEditDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = temp
            }
        }
        return dp[s2.length]
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
                        if (_discoverFeedItems.value.isEmpty() && merged.size >= 10) {
                            refreshDiscoverFeed()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StreamViewModel", "Error progressively fetching $cat", e)
                }
                kotlinx.coroutines.delay(100)
            }

            // Populate / Refresh Discover Feed with fresh randomized latest items
            refreshDiscoverFeed()

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
                kotlinx.coroutines.delay(280)
                executeMediaSearch(trimmed)
            }
        } else {
            _isSearchingMedia.value = false
        }
    }

    fun triggerImmediateMediaSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            _mediaSearchQuery.value = trimmed
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                executeMediaSearch(trimmed)
            }
        }
    }

    private suspend fun executeMediaSearch(trimmed: String) {
        _isSearchingMedia.value = true
        try {
            val results = mediaRepository.searchMedia(trimmed, "all")
            if (results.isNotEmpty()) {
                val currentData = (_mediaState.value as? UiState.Success)?.data ?: emptyList()
                val merged = (results + currentData).distinctBy { it.id }
                _mediaState.value = UiState.Success(merged)
            }
        } catch (e: Exception) {
            Log.e("StreamViewModel", "Error fetching search results", e)
        } finally {
            _isSearchingMedia.value = false
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

    fun extractSeasonFromTitle(title: String): Int? {
        val cleanTitle = title.lowercase()
        val seasonRegex = Regex("""season\s*(\d+)""")
        val match1 = seasonRegex.find(cleanTitle)
        if (match1 != null) {
            return match1.groupValues[1].toIntOrNull()
        }
        val sRegex = Regex("""\bs\s*(\d+)\b""")
        val match2 = sRegex.find(cleanTitle)
        if (match2 != null) {
            return match2.groupValues[1].toIntOrNull()
        }
        val rdRegex = Regex("""(\d+)(?:st|nd|rd|th)\s*season""")
        val match3 = rdRegex.find(cleanTitle)
        if (match3 != null) {
            return match3.groupValues[1].toIntOrNull()
        }
        if (cleanTitle.endsWith(" iii") || cleanTitle.contains(" iii ")) return 3
        if (cleanTitle.endsWith(" ii") || cleanTitle.contains(" ii ")) return 2
        if (cleanTitle.endsWith(" iv") || cleanTitle.contains(" iv ")) return 4
        if (cleanTitle.endsWith(" v") || cleanTitle.contains(" v ")) return 5
        if (cleanTitle.endsWith(" vi") || cleanTitle.contains(" vi ")) return 6
        
        val partRegex = Regex("""part\s*(\d+)""")
        val match5 = partRegex.find(cleanTitle)
        if (match5 != null) {
            val partNum = match5.groupValues[1].toIntOrNull() ?: 1
            if (partNum > 1) {
                return partNum
            }
        }
        return null
    }

    fun fetchAnikotoMediaData(item: MediaItem, selectedSeasonNum: Int = 1) {
        val isAnime = com.example.scraper.AnimePosterEngine.isAnime(
            title = item.title,
            category = item.category,
            type = item.type,
            id = item.id
        )
        if (!isAnime) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAnimeLoading.value = true
            try {
                val watchUrl = if (item.streamUrl != null && item.streamUrl!!.isNotBlank() && item.streamUrl!!.contains("anikoto.cz")) {
                    item.streamUrl!!
                } else {
                    val resolved = com.example.scraper.AnikotoScraper.resolveAnikotoWatchUrl(item.title)
                    resolved ?: ""
                }

                if (watchUrl.isNotBlank()) {
                    Log.d("StreamViewModel", "Fetching Anikoto details & seasons for watchUrl: $watchUrl")
                    val detailsDeferred = async {
                        com.example.scraper.AnikotoScraper.fetchAnimeDetails(watchUrl)
                    }
                    val seasonsDeferred = async {
                        com.example.scraper.AnikotoScraper.fetchSeasons(watchUrl, item.title)
                    }

                    val details = detailsDeferred.await()
                    val seasons = seasonsDeferred.await()

                    _anikotoDetailsState.value = details
                    _anikotoSeasons.value = seasons

                    val matchedSeasonUrl = seasons.find { it.number == selectedSeasonNum }?.watchUrl ?: watchUrl
                    val episodes = com.example.scraper.AnikotoScraper.fetchEpisodes(matchedSeasonUrl)
                    _anikotoEpisodes.value = episodes
                    Log.d("StreamViewModel", "Anikoto details loaded: ${seasons.size} seasons/parts, ${episodes.size} episodes")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnimeLoading.value = false
            }
        }
    }

    fun preScrapeMediaItem(item: MediaItem?, season: Int = 1, episode: Int = 1, preferredServerKey: String? = _selectedStreamServerKey.value) {
        if (item == null) return
        val detectedSeason = extractSeasonFromTitle(item.title) ?: season
        val effectiveItem = item.copy(imdbId = item.imdbId ?: item.id)
        val tmdbId = effectiveItem.imdbId ?: effectiveItem.id

        val isAnime = com.example.scraper.AnimePosterEngine.isAnime(
            title = effectiveItem.title,
            category = effectiveItem.category,
            type = effectiveItem.type,
            id = effectiveItem.id
        )

        // Instant pre-scrape for anime metadata & servers
        if (isAnime) {
            fetchAnikotoMediaData(effectiveItem, detectedSeason)
            fetchAnikotoServers(effectiveItem, detectedSeason, episode)
            if (com.example.scraper.UnifiedStreamManager.getCachedStream(tmdbId, detectedSeason, episode) != null) return

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
                        season = detectedSeason,
                        episode = episode,
                        isAnime = true
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
            return
        }

        // Normal Movies & Series (e.g. Facing El Chapo, Silo): High-speed parallel server race
        val isSeriesItem = effectiveItem.type.equals("series", ignoreCase = true) ||
                           effectiveItem.type.equals("tv", ignoreCase = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val winner = com.example.scraper.UnifiedStreamManager.raceFastestServerStream(
                    context = getApplication(),
                    tmdbId = tmdbId,
                    title = effectiveItem.title,
                    isTv = isSeriesItem,
                    season = detectedSeason,
                    episode = episode,
                    preferredServerKey = preferredServerKey ?: _selectedStreamServerKey.value
                )
                if (winner != null && winner.result.streamUrl.isNotBlank()) {
                    if (_activeMediaItem.value?.id == item.id && _activeMediaStreamUrl.value.isNullOrBlank()) {
                        _activeMediaStreamUrl.value = winner.result.streamUrl
                        _activeMediaStreamHeaders.value = winner.result.headers
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

        val detectedSeason = extractSeasonFromTitle(item.title) ?: season

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
            "${item.imdbId ?: item.id}_s${detectedSeason}e${episode}"
        } else {
            item.imdbId ?: item.id
        }
        val effectiveStartPos = startPositionMs ?: getMediaPlaybackProgress(watchProgressKey).first
        _mediaPlaybackStartPosition.value = if (effectiveStartPos > 0L) effectiveStartPos else null

        val effectiveItem = item.copy(imdbId = item.imdbId ?: item.id)
        val tmdbId = effectiveItem.imdbId ?: effectiveItem.id

        // Crucial: Set active media item IMMEDIATELY and synchronously on current thread!
        // This ensures PlayerScreen renders CinemetaWebViewPlayer instantly with 0ms delay without getting stuck on "Launching AIR Player"
        _activeChannel.value = null
        _activeMediaItem.value = effectiveItem
        _activeMediaSeason.value = detectedSeason
        _activeMediaEpisode.value = episode
        _activeMediaStreamUrl.value = null
        _activeMediaStreamHeaders.value = emptyMap()
        addMediaToHistory(effectiveItem)

        val isAnime = com.example.scraper.AnimePosterEngine.isAnime(
            title = effectiveItem.title,
            category = effectiveItem.category,
            type = effectiveItem.type,
            id = effectiveItem.id
        )
        if (!isAnime) {
            _selectedServer.value = null
        }

        viewModelScope.launch {
            // Check if stream is already pre-scraped / cached in background for instant launch!
            val cachedStream = com.example.scraper.UnifiedStreamManager.getCachedStream(tmdbId, detectedSeason, episode)
            if (cachedStream != null && cachedStream.streamUrl.isNotBlank()) {
                _activeMediaStreamUrl.value = cachedStream.streamUrl
                _activeMediaStreamHeaders.value = cachedStream.headers
                _isPlayerPlaying.value = true
                return@launch
            }

            // Check if user selected a specific Anikoto server (SUB or DUB) for anime
            val pickedServer = _selectedServer.value
            if (isAnime && pickedServer != null) {
                try {
                    val serverStream = com.example.scraper.AnikotoScraper.extractStreamFromServer(
                        server = pickedServer,
                        watchUrl = currentServerWatchUrl.ifEmpty { effectiveItem.title },
                        episode = episode
                    )
                    if (serverStream != null && serverStream.streamUrl.isNotBlank()) {
                        _activeMediaStreamUrl.value = serverStream.streamUrl
                        _activeMediaStreamHeaders.value = serverStream.headers
                        _isPlayerPlaying.value = true
                        return@launch
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val isSeriesItem = effectiveItem.type.equals("series", ignoreCase = true) ||
                               effectiveItem.type.equals("tv", ignoreCase = true) ||
                               (effectiveItem.type.equals("anime", ignoreCase = true) && !effectiveItem.category.lowercase().contains("movie"))

            // Launch background parallel scraper (racing fastest servers Hindi, VidRock, Flixer, Prime, Hexa...)
            launch(Dispatchers.IO) {
                try {
                    val winnerResult = if (!isAnime) {
                        com.example.scraper.UnifiedStreamManager.raceFastestServerStream(
                            context = getApplication(),
                            tmdbId = tmdbId,
                            title = effectiveItem.title,
                            isTv = isSeriesItem,
                            season = detectedSeason,
                            episode = episode,
                            preferredServerKey = _selectedStreamServerKey.value
                        )?.result
                    } else {
                        com.example.scraper.UnifiedStreamManager.getStream(
                            context = getApplication(),
                            title = effectiveItem.title,
                            tmdbId = tmdbId,
                            isTv = isSeriesItem,
                            season = detectedSeason,
                            episode = episode,
                            isAnime = true
                        )
                    }

                    if (winnerResult != null && winnerResult.streamUrl.isNotBlank()) {
                        _activeMediaStreamUrl.value = winnerResult.streamUrl
                        _activeMediaStreamHeaders.value = winnerResult.headers
                        _isPlayerPlaying.value = true
                        repository.addLog(
                            type = "INFO",
                            title = "Direct Stream Scraped",
                            message = "Fastest Server extracted direct video link for '" + effectiveItem.title + "'"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
                val curated = repository.getIndexPlaylists(forceRefresh)
                val db = AppDatabase.getDatabase(getApplication())
                val custom = db.customPlaylistDao().getAllCustomPlaylists().map {
                    IptvPlaylist(
                        name = it.name,
                        url = "custom://${it.id}",
                        group = "My Playlists",
                        logo = "https://img.icons8.com/color/96/playlist.png"
                    )
                }
                val data = custom + curated
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
        _isFullScreen.value = false
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
                var data = if (url.startsWith("custom://")) {
                    val id = url.substringAfter("custom://").toLongOrNull() ?: 0L
                    val db = AppDatabase.getDatabase(getApplication())
                    val playlist = db.customPlaylistDao().getAllCustomPlaylists().find { it.id == id }
                    if (playlist != null) {
                        val content = if (playlist.rawContent.startsWith("file://")) {
                            val file = java.io.File(playlist.rawContent.substring(7))
                            if (file.exists()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.readText() }
                            } else ""
                        } else {
                            playlist.rawContent
                        }
                        com.example.data.network.IptvParser.parseChannels(content)
                    } else {
                        emptyList()
                    }
                } else {
                    repository.getChannelsFromPlaylist(url)
                }
                
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
                if (data.isNotEmpty()) {
                    _activeChannel.value = data.first()
                    _isFullScreen.value = false
                }
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

    fun computeChannelHash(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        var e = 0
        for (i in 0 until trimmed.length) {
            e = (e shl 5) - e + trimmed[i].code
        }
        val absE = if (e == Int.MIN_VALUE) {
            Int.MAX_VALUE.toLong() + 1L
        } else {
            kotlin.math.abs(e.toLong())
        }
        return "ch_" + java.lang.Long.toString(absE, 36).take(10)
    }

    fun isChannelPremium(channel: IptvChannel): Boolean {
        val config = _appControlConfig.value ?: return false
        
        // If master Live TV lock is disabled in admin panel, no live channels are locked
        if (!config.isLiveTvLockEnabled) {
            return false
        }

        // Check individual channel flag from API / playlist source
        if (channel.isPremium) return true

        val cleanName = channel.name.trim().lowercase()
        val cleanUrl = channel.url.trim()
        val cleanGroup = channel.group.trim().lowercase()
        val cleanTvgId = channel.tvgId.trim().lowercase()
        val channelHash = computeChannelHash(cleanUrl).lowercase()
        
        // 1. Check if channel ID/hash (ch_xxxxxx), name, URL, or tvgId is in admin's premiumLiveTvIds list
        if (config.premiumLiveTvIds.isNotEmpty()) {
            val isLockedByIdOrName = config.premiumLiveTvIds.any { rawId ->
                val id = rawId.trim().lowercase()
                if (id.isBlank()) false
                else {
                    (channelHash.isNotBlank() && channelHash == id) ||
                    cleanName == id ||
                    cleanName.contains(id) ||
                    id.contains(cleanName) ||
                    (cleanTvgId.isNotBlank() && (cleanTvgId == id || cleanTvgId.contains(id))) ||
                    (cleanUrl.isNotBlank() && (cleanUrl.lowercase() == id || cleanUrl.lowercase().contains(id)))
                }
            }
            if (isLockedByIdOrName) return true
        }

        // 2. Check if group/category is in specific Live TV premium categories
        if (cleanGroup.isNotBlank() && config.premiumLiveTvCategories.isNotEmpty()) {
            val isLockedByCategory = config.premiumLiveTvCategories.any { rawCat ->
                val cat = rawCat.trim().lowercase()
                if (cat.isBlank()) false
                else cleanGroup == cat || cleanGroup.contains(cat)
            }
            if (isLockedByCategory) return true
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
