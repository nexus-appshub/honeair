package com.example.ui.components
import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType


import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaItem
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.NeonPurple
import com.example.download.MediaDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CinemetaWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Force hardware acceleration for smooth 60fps rendering
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mediaPlaybackRequiresUserGesture = false
                    // Disable safe browsing overhead check to load stream links 2x faster!
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val requestUrl = request?.url?.toString() ?: return false
                        if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                            if (requestUrl.contains("youtube.com/watch") || requestUrl.contains("youtu.be/")) {
                                return true // Block redirecting or launching YouTube app
                            }
                            return false
                        }
                        return true // Block non-http/https protocols like intent://, market://
                    }
                }
                webChromeClient = WebChromeClient()
                
                setTag(url)
                loadYouTubeOrUrl(this, url)
            }
        },
        update = { webView ->
            if (webView.getTag() as? String != url) {
                webView.setTag(url)
                loadYouTubeOrUrl(webView, url)
            }
        },
        modifier = modifier
    )
}

private fun loadYouTubeOrUrl(webView: WebView, url: String) {
    if (url.contains("youtube.com/embed") || url.contains("youtube-nocookie.com/embed")) {
        // Use Desktop User Agent for YouTube to bypass any mobile embedding restrictions (error 152-4 / 153)!
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        val embedUrl = if (!url.contains("origin=")) {
            if (url.contains("?")) "$url&enablejsapi=1&origin=https://www.youtube.com&modestbranding=1&rel=0&autoplay=1&playsinline=1"
            else "$url?autoplay=1&enablejsapi=1&origin=https://www.youtube.com&modestbranding=1&rel=0&playsinline=1"
        } else url

        val headers = mutableMapOf<String, String>()
        headers["Referer"] = "https://www.youtube.com"
        headers["Origin"] = "https://www.youtube.com"
        webView.loadUrl(embedUrl, headers)
    } else {
        // Restore default mobile user agent for normal web player streaming links
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val headers = mutableMapOf<String, String>()
        headers["Referer"] = "https://www.youtube.com"
        webView.loadUrl(url, headers)
    }
}

data class CastMember(
    val name: String,
    val character: String,
    val profilePath: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemetaWebViewPlayer(isMiniPlayer: Boolean = false, onMiniPlayerToggle: () -> Unit = {}, 
    imdbId: String,
    title: String,
    type: String = "movie", // "movie" or "series"
    nativeStreamUrl: String? = null,
    nativeHeaders: Map<String, String> = emptyMap(),
    season: Int = 1,
    episode: Int = 1,
    allMediaItems: List<MediaItem> = emptyList(),
    onSelectMedia: (MediaItem) -> Unit = {},
    modifier: Modifier = Modifier,
    onClosePlayer: () -> Unit = {},
    onFullScreenChange: (Boolean) -> Unit = {},
    isFullScreen: Boolean = false,
    isInPipMode: Boolean = false,
    onPlayingStateChanged: (Boolean) -> Unit = {},
    viewModel: com.example.ui.viewmodel.StreamViewModel,
    mediaItem: MediaItem? = null
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showDownloaderModal by remember { mutableStateOf(false) }
    var showServerDropdown by remember { mutableStateOf(false) }
    var canGoBackState by remember { mutableStateOf(false) }

    val userProfile by viewModel.userProfile.collectAsState()
    val subServers by viewModel.availableSubServers.collectAsState()
    val dubServers by viewModel.availableDubServers.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val isFetchingServers by viewModel.isFetchingServers.collectAsState()
    val mediaDetailState by viewModel.mediaDetailState.collectAsState()
    val verifiedServers by viewModel.verifiedStreamServers.collectAsState()
    val isDeepScraping by viewModel.isDeepScrapingServers.collectAsState()

    // Keep Screen On while CinemetaWebViewPlayer is active
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isInPipMode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!isInPipMode) {
                        webViewRef?.onPause()
                        webViewRef?.pauseTimers()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    webViewRef?.onResume()
                    webViewRef?.resumeTimers()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var currentSeason by remember(imdbId, season) { mutableIntStateOf(season) }
    var currentEpisode by remember(imdbId, episode) { mutableIntStateOf(episode) }
    var capturedVideoUrl by remember(imdbId, currentSeason, currentEpisode) { mutableStateOf<String?>(null) }
    var activeSubtitles by remember(imdbId, currentSeason, currentEpisode) { mutableStateOf<List<com.example.scraper.SubtitleTrack>>(emptyList()) }
    var selectedVidnestServerKey by remember(imdbId, currentSeason, currentEpisode) { mutableStateOf<String?>(null) }
    var customScrapedHeaders by remember(nativeHeaders) { mutableStateOf(nativeHeaders) }
    var isMainSelected by remember(imdbId, currentSeason, currentEpisode) { mutableStateOf(true) }
    var mainScrapedVideoUrl by remember(imdbId, currentSeason, currentEpisode) { mutableStateOf<String?>(null) }
    var mainScrapedHeaders by remember(imdbId, currentSeason, currentEpisode) { mutableStateOf<Map<String, String>?>(null) }
    var isScrapingDirectStream by remember { mutableStateOf(false) }
    var directScrapeSecondsRemaining by remember(imdbId, currentSeason, currentEpisode) { androidx.compose.runtime.mutableIntStateOf(0) }
    var directScrapeAttemptCount by remember(imdbId, currentSeason, currentEpisode) { androidx.compose.runtime.mutableIntStateOf(0) }
    var directScrapeStatusText by remember(imdbId, currentSeason, currentEpisode) { mutableStateOf("Scanning 12+ cloud streams in parallel...") }
    var useExoPlayer by remember(nativeStreamUrl) { mutableStateOf(true) }
    val failedAnimeUrls = remember(imdbId, currentSeason, currentEpisode) { mutableStateListOf<String>() }

    val anikotoSeasons by viewModel.anikotoSeasons.collectAsState()
    val anikotoEpisodes by viewModel.anikotoEpisodes.collectAsState()
    val isAnimeLoading by viewModel.isAnimeLoading.collectAsState()
    val isVipUser by com.example.subscription.SubscriptionManager.isPremium.collectAsState()
    var showSubscriptionPlanModal by remember { mutableStateOf(false) }

    val currentMediaItem = remember(imdbId, title, mediaItem, allMediaItems) {
        mediaItem
            ?: allMediaItems.find { it.imdbId == imdbId && it.title == title }
            ?: allMediaItems.find { it.imdbId == imdbId }
            ?: allMediaItems.find { it.title == title }
    }

    val isSeries = remember(imdbId, type, currentMediaItem, mediaDetailState, currentSeason, currentEpisode, season, episode) {
        val rawId = imdbId.lowercase()
        val itemId = currentMediaItem?.id?.lowercase() ?: ""
        val t = type.lowercase()
        val cat = currentMediaItem?.category?.lowercase() ?: ""
        val itemType = currentMediaItem?.type?.lowercase() ?: ""
        val hasSeasonsInDetail = (mediaDetailState?.number_of_seasons != null && (mediaDetailState?.number_of_seasons ?: 0) > 0)
        val isExplicitMovieInDetail = (mediaDetailState?.runtime != null && (mediaDetailState?.number_of_seasons ?: 0) == 0)
        val isEpisodic = (currentMediaItem?.episodes?.isNotBlank() == true)
        val hasMultipleEpisodes = currentEpisode > 1 || currentSeason > 1 || episode > 1 || season > 1
        val isSeriesCategory = cat.contains("series") || cat.contains("tv show") || cat.contains("natok") || cat.contains("drama") || cat.contains("k-drama") || cat.contains("show")
        val isSeriesType = t == "series" || t == "tv" || itemType == "series" || itemType == "tv" || t == "anime" || itemType == "anime"
        val isSeriesId = rawId.startsWith("series_") || itemId.startsWith("series_") || rawId.startsWith("anikoto_") || itemId.startsWith("anikoto_")

        if (isSeriesType || isSeriesCategory || isSeriesId || hasSeasonsInDetail || isEpisodic || hasMultipleEpisodes) {
            true
        } else if (isExplicitMovieInDetail || t == "movie" || itemType == "movie" || rawId.startsWith("movie_") || itemId.startsWith("movie_")) {
            false
        } else {
            false
        }
    }
    val isNativeMatching = (currentSeason == season && currentEpisode == episode)
    val effectiveNativeUrl = if (isNativeMatching && !nativeStreamUrl.isNullOrBlank()) nativeStreamUrl else null

    val vmActiveStreamUrl by viewModel.activeMediaStreamUrl.collectAsState()
    val vmActiveStreamHeaders by viewModel.activeMediaStreamHeaders.collectAsState()
    val vmActiveSeason by viewModel.activeMediaSeason.collectAsState()
    val vmActiveEpisode by viewModel.activeMediaEpisode.collectAsState()

    LaunchedEffect(vmActiveStreamUrl, vmActiveSeason, vmActiveEpisode, currentSeason, currentEpisode) {
        if (!vmActiveStreamUrl.isNullOrBlank() && (vmActiveSeason == currentSeason && vmActiveEpisode == currentEpisode)) {
            if (capturedVideoUrl != vmActiveStreamUrl) {
                capturedVideoUrl = vmActiveStreamUrl
                customScrapedHeaders = vmActiveStreamHeaders
                mainScrapedVideoUrl = vmActiveStreamUrl
                mainScrapedHeaders = vmActiveStreamHeaders
                useExoPlayer = true
                isScrapingDirectStream = false
                directScrapeSecondsRemaining = 0
            }
        }
    }

    LaunchedEffect(nativeStreamUrl, currentSeason, currentEpisode) {
        if (!nativeStreamUrl.isNullOrBlank() && currentSeason == season && currentEpisode == episode) {
            capturedVideoUrl = nativeStreamUrl
            customScrapedHeaders = nativeHeaders
            useExoPlayer = true
            isScrapingDirectStream = false
            directScrapeSecondsRemaining = 0
        }
    }

    val isAnime = remember(currentMediaItem, title, type) {
        com.example.scraper.AnimePosterEngine.isAnime(
            title = currentMediaItem?.title ?: title,
            category = currentMediaItem?.category,
            type = type,
            id = currentMediaItem?.id ?: imdbId
        )
    }

    // Playback resolution is owned by StreamViewModel. Do not start a second
    // scraper loop here: competing coroutines were causing Episode-1 and server
    // selections to overwrite one another.
    LaunchedEffect(imdbId, title, currentSeason, currentEpisode, isAnime) {
        if (isAnime) {
            val tempItem = currentMediaItem ?: MediaItem(
                id = imdbId,
                imdbId = imdbId,
                title = title,
                category = "Anime",
                imageUrl = "",
                type = type,
                year = "2024"
            )
            viewModel.fetchAnikotoMediaData(tempItem, currentSeason)
            viewModel.fetchAnikotoServers(tempItem, currentSeason, currentEpisode)
        }

        // Mirror the authoritative ViewModel stream into the player surface.
        // The ViewModel owns cancellation, request generation and server selection.
        val vmUrl = viewModel.activeMediaStreamUrl.value
        val vmSeason = viewModel.activeMediaSeason.value
        val vmEpisode = viewModel.activeMediaEpisode.value
        if (!vmUrl.isNullOrBlank() && vmSeason == currentSeason && vmEpisode == currentEpisode) {
            capturedVideoUrl = vmUrl
            customScrapedHeaders = viewModel.activeMediaStreamHeaders.value
            mainScrapedVideoUrl = vmUrl
            mainScrapedHeaders = viewModel.activeMediaStreamHeaders.value
            useExoPlayer = true
            isScrapingDirectStream = false
            directScrapeSecondsRemaining = 0
        }
    }
    // Universal automated subtitle fetching for stream enhancement
        scope.launch(Dispatchers.IO) {
            try {
                val fetchedSubs = com.example.scraper.SubtitleFetcher.fetchSubtitles(
                    tmdbId = imdbId,
                    imdbId = currentMediaItem?.imdbId ?: imdbId,
                    title = title,
                    isTv = isSeries,
                    season = currentSeason,
                    episode = currentEpisode
                )
                if (fetchedSubs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val combined = (activeSubtitles + fetchedSubs).distinctBy { it.url }
                        activeSubtitles = combined
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val restoreSystemBars = remember(activity) {
        {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    var currentServerIndex by remember(imdbId, type, currentSeason, currentEpisode) { androidx.compose.runtime.mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var isFullScreenVideo by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val isMediaPlaying by viewModel.isMediaPlaying.collectAsState()
    val youtubeVideoId by viewModel.youtubeVideoId.collectAsState()
    val youtubeTrailerId by viewModel.youtubeTrailerId.collectAsState()
    var showTrailerDialog by remember { mutableStateOf(false) }
    var selectedDetailItem by remember { mutableStateOf<com.example.data.model.MediaItem?>(null) }

    // Auto-extract stream when user or server group sets the selected Anikoto server (preserves SUB / DUB)
    LaunchedEffect(selectedServer) {
        val srv = selectedServer
        if (isAnime && srv != null) {
            withContext(Dispatchers.IO) {
                val extracted = com.example.scraper.UnifiedStreamManager.getStream(
                    context = context,
                    title = title,
                    tmdbId = imdbId,
                    isTv = isSeries,
                    season = currentSeason,
                    episode = currentEpisode,
                    isAnime = true,
                    audioType = srv.type.lowercase(),
                    requestedServerKey = srv.id
                )
                if (extracted != null && extracted.streamUrl.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        capturedVideoUrl = extracted.streamUrl
                        customScrapedHeaders = extracted.headers
                        if (extracted.subtitles.isNotEmpty()) {
                            activeSubtitles = extracted.subtitles
                        }
                        useExoPlayer = true
                        isLoading = false
                        hasError = false
                    }
                }
            }
        }
    }

    LaunchedEffect(currentMediaItem) {
        viewModel.clearYouTubeTrailerId()
        currentMediaItem?.let {
            viewModel.fetchYouTubeTrailerId(it.title, it.year)
        }
    }

    val isBangla = remember(currentMediaItem) {
        currentMediaItem?.category?.equals("Bangla Cinema & Natok", ignoreCase = true) == true
    }

    LaunchedEffect(imdbId, currentSeason, currentEpisode, isBangla) {
        if (isBangla) {
            viewModel.clearYouTubeVideoId()
            viewModel.fetchYouTubeVideoId(
                title = title,
                year = currentMediaItem?.year ?: "",
                season = currentSeason,
                episode = currentEpisode,
                isSeries = isSeries
            )
        } else {
            viewModel.clearYouTubeVideoId()
        }
    }

    LaunchedEffect(imdbId, type) {
        if (imdbId.isNotBlank()) {
            viewModel.fetchMediaDetails(imdbId, type)
        }
    }

    LaunchedEffect(isMediaPlaying) {
        if (!isMediaPlaying) {
            webViewRef?.onPause()
            webViewRef?.pauseTimers()
        } else {
            webViewRef?.onResume()
            webViewRef?.resumeTimers()
        }
    }

    // Dynamic Cinemeta API Season/Episode Metadata State
    var seasonEpisodeMap by remember(imdbId) { mutableStateOf<Map<Int, List<Int>>>(emptyMap()) }
    var isFetchingMeta by remember(imdbId) { mutableStateOf(false) }

    // Sync Anime episodes & seasons when available from ViewModel
    LaunchedEffect(anikotoEpisodes, anikotoSeasons, isAnime, currentSeason) {
        if (isAnime && anikotoEpisodes.isNotEmpty()) {
            val epNumbers = anikotoEpisodes.map { it.number }.sorted()
            val newMap = seasonEpisodeMap.toMutableMap()
            newMap[currentSeason] = epNumbers
            if (anikotoSeasons.isNotEmpty()) {
                anikotoSeasons.forEach { s ->
                    if (!newMap.containsKey(s.number)) {
                        newMap[s.number] = listOf(1)
                    }
                }
            }
            seasonEpisodeMap = newMap
        }
    }

    LaunchedEffect(imdbId, title, isSeries, isAnime) {
        if (!isSeries || imdbId.isBlank()) return@LaunchedEffect
        if (isAnime) return@LaunchedEffect

        isFetchingMeta = true
        withContext(Dispatchers.IO) {
            try {
                var fetchedMap: Map<Int, List<Int>>? = null

                val client = OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val request = original.newBuilder()
                            .header("X-App-Version", com.example.BuildConfig.VERSION_CODE.toString())
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                var tmdbTvId = imdbId.removePrefix("series_").removePrefix("movie_").removePrefix("anikoto_").trim()
                if (tmdbTvId.startsWith("tt")) {
                    try {
                        val findUrl = "https://api.themoviedb.org/3/find/$tmdbTvId?external_source=imdb_id&api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                        val findReq = Request.Builder().url(findUrl).build()
                        client.newCall(findReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()
                                if (!body.isNullOrEmpty()) {
                                    val j = JSONObject(body)
                                    val tvResults = j.optJSONArray("tv_results")
                                    if (tvResults != null && tvResults.length() > 0) {
                                        val firstTv = tvResults.getJSONObject(0)
                                        val numId = firstTv.optInt("id", 0)
                                        if (numId > 0) {
                                            tmdbTvId = numId.toString()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // If not found yet, search TMDB by title
                if (!tmdbTvId.all { it.isDigit() } && title.isNotBlank()) {
                    try {
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                        val searchUrl = "https://api.themoviedb.org/3/search/tv?query=$encodedTitle&api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                        val sReq = Request.Builder().url(searchUrl).build()
                        client.newCall(sReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()
                                if (!body.isNullOrEmpty()) {
                                    val j = JSONObject(body)
                                    val results = j.optJSONArray("results")
                                    if (results != null && results.length() > 0) {
                                        val firstTv = results.getJSONObject(0)
                                        val numId = firstTv.optInt("id", 0)
                                        if (numId > 0) {
                                            tmdbTvId = numId.toString()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (tmdbTvId.all { it.isDigit() }) {
                    val url = "https://api.themoviedb.org/3/tv/$tmdbTvId?api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                    val request = Request.Builder().url(url).build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string()
                            if (!bodyString.isNullOrEmpty()) {
                                val json = JSONObject(bodyString)
                                val seasons = json.optJSONArray("seasons")
                                if (seasons != null && seasons.length() > 0) {
                                    val tempMap = mutableMapOf<Int, List<Int>>()
                                    for (i in 0 until seasons.length()) {
                                        val s = seasons.getJSONObject(i)
                                        val sNum = s.optInt("season_number", 0)
                                        val eCount = s.optInt("episode_count", 0)
                                        if (sNum > 0 && eCount > 0) {
                                            tempMap[sNum] = (1..eCount).toList()
                                        }
                                    }
                                    if (tempMap.isNotEmpty()) {
                                        fetchedMap = tempMap
                                    }
                                }
                            }
                        }
                    }
                }

                // Fallback to Cinemeta Stremio API if TMDB didn't return episodes
                if (fetchedMap == null && imdbId.startsWith("tt")) {
                    try {
                        val cinemetaUrl = "https://v3-cinemeta.strem.io/meta/series/$imdbId.json"
                        val cinemetaReq = Request.Builder().url(cinemetaUrl).build()
                        client.newCall(cinemetaReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()
                                if (!body.isNullOrEmpty()) {
                                    val j = JSONObject(body)
                                    val meta = j.optJSONObject("meta")
                                    val videos = meta?.optJSONArray("videos")
                                    if (videos != null && videos.length() > 0) {
                                        val tempMap = mutableMapOf<Int, MutableList<Int>>()
                                        for (i in 0 until videos.length()) {
                                            val v = videos.getJSONObject(i)
                                            val sNum = v.optInt("season", 0)
                                            val epNum = v.optInt("episode", 0)
                                            if (sNum > 0 && epNum > 0) {
                                                tempMap.getOrPut(sNum) { mutableListOf() }.add(epNum)
                                            }
                                        }
                                        if (tempMap.isNotEmpty()) {
                                            fetchedMap = tempMap.mapValues { it.value.distinct().sorted() }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (fetchedMap == null) {
                    // Default fallback if not available: 1 season with up to currentEpisode or 24 episodes
                    val maxEp = maxOf(currentEpisode, 24)
                    fetchedMap = mapOf(1 to (1..maxEp).toList())
                }

                withContext(Dispatchers.Main) {
                    seasonEpisodeMap = fetchedMap!!
                    isFetchingMeta = false

                    val availableSeasons = fetchedMap!!.keys.sorted()
                    if (availableSeasons.isNotEmpty() && currentSeason !in availableSeasons) {
                        currentSeason = availableSeasons.first()
                    }
                    val availableEpisodes = fetchedMap!![currentSeason] ?: emptyList()
                    if (availableEpisodes.isNotEmpty() && currentEpisode !in availableEpisodes) {
                        currentEpisode = availableEpisodes.firstOrNull() ?: 1
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    seasonEpisodeMap = mapOf(1 to (1..24).toList())
                    isFetchingMeta = false
                }
            }
        }
    }

    var resolvedTmdbId by remember(imdbId, title) {
        val clean = imdbId.removePrefix("series_").removePrefix("movie_").removePrefix("anikoto_").trim()
        mutableStateOf<String?>(if (clean.isNotBlank() && !clean.startsWith("tt") && clean.all { it.isDigit() }) clean else null)
    }

    LaunchedEffect(imdbId, title, isAnime) {
        if (resolvedTmdbId == null) {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(6, TimeUnit.SECONDS)
                        .readTimeout(6, TimeUnit.SECONDS)
                        .build()

                    var foundId: String? = null

                    // Step 1: If IMDb ID is available, resolve true numerical TMDb ID using Find by External ID API (Crucial for TV shows on vidsrc.sbs)
                    if (imdbId.startsWith("tt")) {
                        val findUrl = "https://api.themoviedb.org/3/find/$imdbId?external_source=imdb_id&api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                        val reqFind = Request.Builder().url(findUrl).build()
                        val respFind = client.newCall(reqFind).execute()
                        if (respFind.isSuccessful) {
                            val body = respFind.body?.string()
                            if (!body.isNullOrEmpty()) {
                                val j = JSONObject(body)
                                val tvResults = j.optJSONArray("tv_results")
                                if (tvResults != null && tvResults.length() > 0) {
                                    val numId = tvResults.getJSONObject(0).optInt("id", 0)
                                    if (numId > 0) foundId = numId.toString()
                                }
                                if (foundId == null) {
                                    val movieResults = j.optJSONArray("movie_results")
                                    if (movieResults != null && movieResults.length() > 0) {
                                        val numId = movieResults.getJSONObject(0).optInt("id", 0)
                                        if (numId > 0) foundId = numId.toString()
                                    }
                                }
                            }
                        }
                    }

                    // Step 2: Fallback to Title Search if External ID resolution was unsuccessful
                    if (foundId == null) {
                        val cleanTitle = title
                            .replace(Regex("""(?i)\s*(?:[-–—:]\s*)?(?:Season\s*\d+|[0-9]+(?:st|nd|rd|th)\s*Season|Part\s*\d+|Cour\s*\d+|Final\s*Season|Final\s*Chapter|II|III|IV|V|VI).*$"""), "")
                            .replace(Regex("""(?i)\s*\((?:TV|Dub|Sub|Official|Uncensored)\)"""), "")
                            .trim()
                        val enc = java.net.URLEncoder.encode(if (cleanTitle.isNotBlank()) cleanTitle else title, "UTF-8")

                        val searchTvUrl = "https://api.themoviedb.org/3/search/tv?query=$enc&api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                        val req = Request.Builder().url(searchTvUrl).build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrEmpty()) {
                                val j = JSONObject(body)
                                val res = j.optJSONArray("results")
                                if (res != null && res.length() > 0) {
                                    val numId = res.getJSONObject(0).optInt("id", 0)
                                    if (numId > 0) foundId = numId.toString()
                                }
                            }
                        }

                        if (foundId == null) {
                            val searchMultiUrl = "https://api.themoviedb.org/3/search/multi?query=$enc&api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                            val reqMulti = Request.Builder().url(searchMultiUrl).build()
                            val respMulti = client.newCall(reqMulti).execute()
                            if (respMulti.isSuccessful) {
                                val body = respMulti.body?.string()
                                if (!body.isNullOrEmpty()) {
                                    val j = JSONObject(body)
                                    val res = j.optJSONArray("results")
                                    if (res != null && res.length() > 0) {
                                        val numId = res.getJSONObject(0).optInt("id", 0)
                                        if (numId > 0) foundId = numId.toString()
                                    }
                                }
                            }
                        }
                    }

                    if (foundId != null) {
                        withContext(Dispatchers.Main) {
                            resolvedTmdbId = foundId
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val encodedTitle = remember(title) {
        try { java.net.URLEncoder.encode(title, "UTF-8") } catch (e: Exception) { title }
    }

    // Primary & Fallback URL calculation (Sr-0 Native, Sr-1 English, Sr-2..Sr-10 Embeds)
    val embedServers = remember(imdbId, resolvedTmdbId, isSeries, currentSeason, currentEpisode, encodedTitle, allMediaItems, nativeStreamUrl, youtubeVideoId, capturedVideoUrl) {
        val servers = mutableListOf<Pair<String, String>>()
        
        // Sr-0: Native Direct Player (ExoPlayer with direct CDN / Scraped stream)
        val directUrl = capturedVideoUrl ?: effectiveNativeUrl ?: "native://sr0"
        servers.add(Pair("Servers", directUrl))

        val cleanRaw = imdbId.removePrefix("series_").removePrefix("movie_").removePrefix("anikoto_").trim()
        val effectiveId = resolvedTmdbId ?: (if (cleanRaw.startsWith("tt") || (cleanRaw.isNotBlank() && cleanRaw.all { it.isDigit() })) cleanRaw else "")
        val isImdb = effectiveId.startsWith("tt")
        val tmdbOrImdb = if (isImdb) "imdb" else "tmdb"
        
        val vidsrcSbsUrl = if (!isSeries) "https://vidsrc.sbs/embed/movie/$effectiveId" else "https://vidsrc.sbs/embed/tv/$effectiveId/$currentSeason/$currentEpisode"
        
        // Sr-1 (English) and Sr-2 to Sr-10 Web Embed Servers
        servers.add(Pair("English", if (!isSeries) "https://vidsrc2.ru/embed/movie/$effectiveId" else "https://vidsrc2.ru/embed/tv/$effectiveId/$currentSeason/$currentEpisode"))
        servers.add(Pair("Sr-1", if (!isSeries) "https://vidnest.fun/movie/$effectiveId" else "https://vidnest.fun/tv/$effectiveId/$currentSeason/$currentEpisode"))
        servers.add(Pair("Sr-2", vidsrcSbsUrl))
        servers.add(Pair("Sr-3", if (!isSeries) "https://vidsrc.to/embed/movie/$effectiveId" else "https://vidsrc.to/embed/tv/$effectiveId/$currentSeason/$currentEpisode"))
        servers.add(Pair("Sr-4", if (!isSeries) "https://vidlink.pro/movie/$effectiveId" else "https://vidlink.pro/tv/$effectiveId/$currentSeason/$currentEpisode"))
        servers.add(Pair("Sr-5", if (!isSeries) "https://vidsrc.me/embed/movie?${tmdbOrImdb}=$effectiveId" else "https://vidsrc.me/embed/tv?${tmdbOrImdb}=$effectiveId&season=$currentSeason&episode=$currentEpisode"))
        servers.add(Pair("Sr-6", if (!isSeries) "https://autoembed.co/movie/tmdb/$effectiveId" else "https://autoembed.co/tv/tmdb/$effectiveId-$currentSeason-$currentEpisode"))
        servers.add(Pair("Sr-7", if (!isSeries) "https://vidsrc.in/embed/movie/$effectiveId" else "https://vidsrc.in/embed/tv/$effectiveId/$currentSeason/$currentEpisode"))
        servers.add(Pair("Sr-8", if (!isSeries) "https://player.smashy.stream/movie/$effectiveId" else "https://player.smashy.stream/tv/$effectiveId?s=$currentSeason&e=$currentEpisode"))
        servers.add(Pair("Sr-9", if (!isSeries) "https://player.videasy.net/movie/$effectiveId" else "https://player.videasy.net/tv/$effectiveId/$currentSeason/$currentEpisode"))
        servers.add(Pair("Sr-10", if (!isSeries) "https://02moviedownloader.site/api/download/movie/$effectiveId" else "https://02moviedownloader.site/api/download/tv/$effectiveId/$currentSeason/$currentEpisode"))

        servers
    }

    val currentEmbedUrl = embedServers[currentServerIndex % embedServers.size].second
    val currentServerName = embedServers[currentServerIndex % embedServers.size].first

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    DisposableEffect(Unit) {
        onPlayingStateChanged(true)
        onDispose {
            onPlayingStateChanged(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            restoreSystemBars()
            onFullScreenChange(false)
        }
    }

    BackHandler(enabled = isFullScreenVideo || customView != null) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            isFullScreenVideo = false
            onFullScreenChange(false)
            restoreSystemBars()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            isFullScreenVideo = false
            onFullScreenChange(false)
            restoreSystemBars()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Search query & expanded search bar state for related items
    var localSearchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var onlineSearchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableStateOf("recommend") } // "recommend" or "comments"
    var castList by remember { mutableStateOf<List<CastMember>>(emptyList()) }
    var isFetchingCast by remember { mutableStateOf(false) }

    val globalMediaState by viewModel.mediaState.collectAsState()

    LaunchedEffect(localSearchQuery) {
        val q = localSearchQuery.trim()
        if (q.isBlank()) {
            onlineSearchResults = emptyList()
            isSearchingOnline = false
            return@LaunchedEffect
        }
        isSearchingOnline = true
        kotlinx.coroutines.delay(200)
        try {
            val results = viewModel.searchMediaDirect(q)
            onlineSearchResults = results
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isSearchingOnline = false
        }
    }

    LaunchedEffect(imdbId, type) {
        if (imdbId.startsWith("tt")) {
            isFetchingCast = true
            withContext(Dispatchers.IO) {
                try {
                    val tmdbType = if (type == "series") "tv" else "movie"
                    var finalId = imdbId
                    
                    // Resolve numeric TMDB ID from IMDb ID if needed
                    if (finalId.startsWith("tt")) {
                        val findUrl = "https://api.themoviedb.org/3/find/$finalId?external_source=imdb_id&api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                        val findRequest = Request.Builder().url(findUrl).build()
                        val client = OkHttpClient.Builder().build()
                        val findResponse = client.newCall(findRequest).execute()
                        if (findResponse.isSuccessful) {
                            val findBody = findResponse.body?.string()
                            if (findBody != null) {
                                val findJson = JSONObject(findBody)
                                val results = if (tmdbType == "movie") findJson.optJSONArray("movie_results") else findJson.optJSONArray("tv_results")
                                if (results != null && results.length() > 0) {
                                    finalId = results.getJSONObject(0).getInt("id").toString()
                                }
                            }
                        }
                    }

                    val url = "https://api.themoviedb.org/3/$tmdbType/$finalId/credits?api_key=a359b11d9aa4c4803d25ef86cf7fb19c"
                    val request = Request.Builder().url(url).build()
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val castArray = json.getJSONArray("cast")
                            val tempCast = mutableListOf<CastMember>()
                            for (i in 0 until minOf(castArray.length(), 15)) {
                                val obj = castArray.getJSONObject(i)
                                tempCast.add(
                                    CastMember(
                                        name = obj.getString("name"),
                                        character = obj.optString("character", ""),
                                        profilePath = obj.optString("profile_path", null)
                                    )
                                )
                            }
                            withContext(Dispatchers.Main) {
                                castList = tempCast
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isFetchingCast = false
                }
            }
        }
    }
    
    val globalMediaPool = remember(allMediaItems, globalMediaState) {
        val stateItems = (globalMediaState as? com.example.ui.viewmodel.UiState.Success)?.data ?: emptyList()
        (allMediaItems + stateItems).distinctBy { it.id }
    }

    val searchResultsList = remember(localSearchQuery, globalMediaPool, onlineSearchResults) {
        val q = localSearchQuery.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            val localMatches = viewModel.fuzzySearchMedia(q, globalMediaPool)
            (localMatches + onlineSearchResults).distinctBy { it.id }
        }
    }

    val currentMediaItemForRelated = remember(globalMediaPool, imdbId, title) {
        globalMediaPool.find { it.imdbId == imdbId && it.title == title }
            ?: globalMediaPool.find { it.imdbId == imdbId }
            ?: globalMediaPool.find { it.title == title }
    }
    
    val filteredRelated = remember(globalMediaPool, imdbId, type, currentMediaItemForRelated) {
        val playingItem = currentMediaItemForRelated
        if (playingItem == null) {
            globalMediaPool.filter { item -> item.imdbId != imdbId }
        } else {
            val genreKeywords = listOf(
                "horror", "comedy", "action", "romance", "thriller", "drama", "fantasy", 
                "crime", "mystery", "sci-fi", "science fiction", "family", "adventure", 
                "animation", "romantic", "history", "historical", "war", "documentary", 
                "natok", "cinema", "blockbuster", "love", "scary", "ghost", "dark", "supernatural"
            )
            val playingDescLower = (playingItem.description + " " + playingItem.title).lowercase()
            val playingKeywords = genreKeywords.filter { playingDescLower.contains(it) }
            val playingYearInt = playingItem.year.toIntOrNull()

            globalMediaPool
                .filter { item ->
                    val isDifferentItem = item.id != playingItem.id && item.imdbId != playingItem.imdbId
                    isDifferentItem
                }
                .map { item ->
                    var score = 0.0
                    
                    // 1. Category Matching (Highest Priority - Bangla content stays with Bangla, Anime with Anime, etc.)
                    if (item.category.equals(playingItem.category, ignoreCase = true)) {
                        score += 50.0
                    } else {
                        val catLower = item.category.lowercase()
                        val playCatLower = playingItem.category.lowercase()
                        if (catLower.contains("anime") && playCatLower.contains("anime")) {
                            score += 15.0
                        } else if (catLower.contains("hindi") && playCatLower.contains("hindi")) {
                            score += 15.0
                        } else if (catLower.contains("bangla") && playCatLower.contains("bangla")) {
                            score += 45.0
                        }
                    }
                    
                    // 2. Type Matching (Movie vs Series)
                    if (item.type.equals(playingItem.type, ignoreCase = true)) {
                        score += 10.0
                    }
                    
                    // 3. Year Matching (Closer years get higher match score)
                    val itemYearInt = item.year.toIntOrNull()
                    if (playingYearInt != null && itemYearInt != null) {
                        val diff = kotlin.math.abs(playingYearInt - itemYearInt)
                        when {
                            diff == 0 -> score += 20.0
                            diff <= 1 -> score += 15.0
                            diff <= 2 -> score += 10.0
                            diff <= 5 -> score += 5.0
                        }
                    }
                    
                    // 4. Genre / Theme Matching (Horror, Comedy, Action, etc.)
                    val itemDescLower = (item.description + " " + item.title).lowercase()
                    var genreMatchesCount = 0
                    playingKeywords.forEach { keyword ->
                        if (itemDescLower.contains(keyword)) {
                            score += 15.0
                            genreMatchesCount++
                        }
                    }
                    if (genreMatchesCount > 0) {
                        score += 10.0 // Bonus for having at least one matching genre keyword
                    }
                    
                    // 5. Rating as tie-breaker
                    val r = item.rating.toDoubleOrNull() ?: 0.0
                    score += r * 0.5
                    
                    Pair(item, score)
                }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }

    if (customView != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    val parent = customView?.parent as? ViewGroup
                    parent?.removeView(customView)
                    customView?.keepScreenOn = true
                    customView!!
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val playerBgColor = if (isDark) SpaceBlack else Color(0xFFF5F5F7)

    Box(modifier = modifier.fillMaxSize().background(playerBgColor)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("cinemeta_webview_player")
        ) {
        // 1. Top Clean Header Bar with Control Buttons & Expandable Search Bar
        if (!isInPipMode && !isFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
            AnimatedContent(
                targetState = isSearchExpanded || localSearchQuery.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInHorizontally { width -> width })
                        .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally { width -> -width })
                },
                label = "HeaderSearchTransition"
            ) { searchActive ->
                if (searchActive) {
                    // Expanded Top Search Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(DeepSlate)
                            .border(BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)), RoundedCornerShape(22.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isSearchExpanded = false
                                localSearchQuery = ""
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (localSearchQuery.isEmpty()) {
                                Text(
                                    text = "Search related movies, anime & TV shows...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = localSearchQuery,
                                onValueChange = { localSearchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush = SolidColor(NeonCyan),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (localSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { localSearchQuery = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Normal Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onClosePlayer,
                            modifier = Modifier
                                .size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Close Player",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Download Icon Button
                            IconButton(
                                onClick = { showDownloaderModal = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Video",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Picture-in-Picture Button
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                IconButton(
                                    onClick = {
                                        try {
                                            onMiniPlayerToggle()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "PiP",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Fallback Switch Button Badge (3s Hold / Tap opens Scrollable Server Dropdown)
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { 
                                                currentServerIndex = (currentServerIndex + 1) % embedServers.size
                                                isLoading = true
                                                hasError = false
                                            },
                                            onLongPress = { showServerDropdown = true }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Server",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${embedServers[currentServerIndex % embedServers.size].first.substringBefore(" ")} ▼",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // 2. Video Player Frame (Standard 16:9 ratio stream box - edge to edge)
        Box(
            modifier = if (isInPipMode || isFullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
            }
            .background(Color.Black)
        ) {
            com.example.ui.components.DownloaderModal(
                showModal = showDownloaderModal,
                onDismiss = { showDownloaderModal = false },
                title = title,
                imdbId = imdbId,
                season = currentSeason,
                episode = currentEpisode,
                isSeries = isSeries,
                isAnime = isAnime,
                capturedVideoUrl = capturedVideoUrl ?: effectiveNativeUrl,
                coroutineScope = scope,
                userProfile = userProfile,
                viewModel = viewModel
            )

            val watchProgressKey = remember(imdbId, isSeries, currentSeason, currentEpisode) {
                if (isSeries) "${imdbId}_s${currentSeason}e${currentEpisode}" else imdbId
            }
            val initialStartPos = remember(watchProgressKey) {
                val startFromVm = viewModel.mediaPlaybackStartPosition.value
                if (startFromVm != null) {
                    viewModel.setMediaPlaybackStartPosition(null)
                    startFromVm
                } else {
                    viewModel.getMediaPlaybackProgress(watchProgressKey).first
                }
            }

            val playableDirectUrl = capturedVideoUrl ?: effectiveNativeUrl
            val isCurrentNativeServer = (currentServerIndex % embedServers.size) == 0
            
            if (isCurrentNativeServer) {
                // SR-0: Direct Native ExoPlayer
                if (!playableDirectUrl.isNullOrBlank()) {
                    com.example.ui.components.MovieExoPlayerView(
                        streamUrl = playableDirectUrl,
                        channelName = title,
                        isFullScreen = isFullScreen,
                        isInPipMode = isInPipMode,
                        customHeaders = customScrapedHeaders,
                        subtitles = activeSubtitles,
                        onFullScreenToggle = { onFullScreenChange(!isFullScreen) },
                        onPlaybackError = { _ ->
                            val failedUrl = capturedVideoUrl
                            if (!failedUrl.isNullOrBlank()) {
                                failedAnimeUrls.add(failedUrl)
                            }
                            com.example.scraper.UnifiedStreamManager.invalidateCache(imdbId, currentSeason, currentEpisode, failedUrl, context)
                            if (isAnime) {
                                // For Anime: NEVER fallback to English web embed (vidsrc2.ru)
                                capturedVideoUrl = null
                                useExoPlayer = true
                                isScrapingDirectStream = true
                                scope.launch(Dispatchers.IO) {
                                    val watchUrl = viewModel.currentServerWatchUrl.ifEmpty { title }
                                    val candidateServers = (subServers + dubServers).distinctBy { (it.id.ifBlank { it.streamUrl }) + "_" + it.type }
                                    var workingExtracted: com.example.scraper.ScrapedStreamResult? = null
                                    var workingSrv: com.example.scraper.AnikotoServer? = null

                                    for (srv in candidateServers) {
                                        if (srv.streamUrl.isNotBlank() && failedAnimeUrls.contains(srv.streamUrl)) continue
                                        
                                        val extracted = if (srv.streamUrl.isNotBlank()) {
                                            com.example.scraper.ScrapedStreamResult(
                                                streamUrl = srv.streamUrl,
                                                headers = mapOf(
                                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                                    "Referer" to if (srv.referer.isNotBlank()) srv.referer else "https://anikoto.cz/",
                                                    "Origin" to "https://anikoto.cz"
                                                ),
                                                referer = if (srv.referer.isNotBlank()) srv.referer else "https://anikoto.cz/",
                                                subtitles = srv.tracks
                                            )
                                        } else {
                                            com.example.scraper.UnifiedStreamManager.getStream(
                                                context = context,
                                                title = title,
                                                tmdbId = imdbId,
                                                isTv = isSeries,
                                                season = currentSeason,
                                                episode = currentEpisode,
                                                isAnime = true,
                                                audioType = srv.type.lowercase(),
                                                requestedServerKey = srv.id
                                            )
                                        }
                                        
                                        if (extracted != null && extracted.streamUrl.isNotBlank() && !failedAnimeUrls.contains(extracted.streamUrl)) {
                                            workingExtracted = extracted
                                            workingSrv = srv
                                            break
                                        }
                                    }

                                    if (workingExtracted == null) {
                                        val directSub = com.example.scraper.UnifiedStreamManager.getStream(
                                            context = context,
                                            title = title,
                                            tmdbId = imdbId,
                                            isTv = isSeries,
                                            season = currentSeason,
                                            episode = currentEpisode,
                                            isAnime = true,
                                            audioType = "sub"
                                        )
                                        if (directSub != null && directSub.streamUrl.isNotBlank() && !failedAnimeUrls.contains(directSub.streamUrl)) {
                                            workingExtracted = directSub
                                        }
                                    }

                                    if (workingExtracted == null) {
                                        val directDub = com.example.scraper.UnifiedStreamManager.getStream(
                                            context = context,
                                            title = title,
                                            tmdbId = imdbId,
                                            isTv = isSeries,
                                            season = currentSeason,
                                            episode = currentEpisode,
                                            isAnime = true,
                                            audioType = "dub"
                                        )
                                        if (directDub != null && directDub.streamUrl.isNotBlank() && !failedAnimeUrls.contains(directDub.streamUrl)) {
                                            workingExtracted = directDub
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        if (workingExtracted != null && workingExtracted.streamUrl.isNotBlank()) {
                                            capturedVideoUrl = workingExtracted.streamUrl
                                            customScrapedHeaders = workingExtracted.headers
                                            if (workingExtracted.subtitles.isNotEmpty()) activeSubtitles = workingExtracted.subtitles
                                            if (workingSrv != null) {
                                                viewModel.selectAnikotoServer(workingSrv)
                                            }
                                            useExoPlayer = true
                                            isScrapingDirectStream = false
                                        } else {
                                            directScrapeAttemptCount++
                                            isScrapingDirectStream = false
                                        }
                                    }
                                }
                            } else if (directScrapeAttemptCount < 3) {
                                // Race all available servers dynamically instead of forcing a specific broken server
                                selectedVidnestServerKey = null
                                capturedVideoUrl = null
                                directScrapeAttemptCount++
                                isScrapingDirectStream = true
                            } else {
                                // Gracefully fallback to Embed Web Player without refreshing loops
                                useExoPlayer = false
                                isScrapingDirectStream = false
                                if (embedServers.size > 1) {
                                    currentServerIndex = 1
                                }
                            }
                        },
                        onBack = onClosePlayer,
                        isSeries = isSeries,
                        onNextEpisode = {
                            val episodesList = seasonEpisodeMap[currentSeason] ?: emptyList()
                            val nextEp = currentEpisode + 1
                            if (episodesList.contains(nextEp)) {
                                currentEpisode = nextEp
                            } else {
                                val nextSeason = currentSeason + 1
                                val nextSeasonEpisodes = seasonEpisodeMap[nextSeason] ?: emptyList()
                                if (nextSeasonEpisodes.isNotEmpty()) {
                                    currentSeason = nextSeason
                                    currentEpisode = nextSeasonEpisodes.first()
                                } else {
                                    currentEpisode = nextEp
                                }
                            }
                        },
                        initialStartPositionMs = initialStartPos,
                        onProgressUpdate = { pos, dur ->
                            viewModel.saveMediaPlaybackProgress(watchProgressKey, pos, dur)
                        },
                        isAnime = isAnime,
                        subServers = subServers,
                        dubServers = dubServers,
                        selectedAnikotoServer = selectedServer,
                        onSelectAnikotoServer = { srv ->
                            // Server selection is authoritative in the ViewModel. Do not
                            // launch a second scraper coroutine from the UI.
                            selectedVidnestServerKey = null
                            isMainSelected = false
                            isLoading = true
                            hasError = false
                            viewModel.selectAnikotoServer(srv, currentEpisode)
                        },
                        embedServers = embedServers,
                        currentEmbedServerIndex = currentServerIndex,
                        onSelectEmbedServerIndex = { idx ->
                            currentServerIndex = idx
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    LaunchedEffect(Unit) {
                        webViewRef?.onPause()
                        webViewRef?.pauseTimers()
                        onPlayingStateChanged(true)
                    }
                } else {
                    // Strict Server 0 Retention: Keep player on Native Direct server without premature auto-switching.
                    // Full scraping runs concurrently across all sub-providers, VidRock and MovieBox.
                    LaunchedEffect(currentServerIndex, playableDirectUrl, isScrapingDirectStream) {
                        // Never auto switch away from Server 0 while scraping is active or during playback.
                    }

                    // Direct Native ExoPlayer Loading State & Stream Recovery UI
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpaceBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScrapingDirectStream || directScrapeSecondsRemaining > 0 || (!isAnime && playableDirectUrl.isNullOrBlank())) {
                            LaunchedEffect(!isScrapingDirectStream, directScrapeSecondsRemaining, playableDirectUrl) {
                                if (!isAnime && playableDirectUrl.isNullOrBlank() && !isScrapingDirectStream && directScrapeSecondsRemaining <= 0) {
                                    val sr2Index = embedServers.indexOfFirst { it.second.contains("vidsrc.sbs") }.takeIf { it >= 0 }
                                        ?: embedServers.indexOfFirst { it.first.contains("Sr-2", ignoreCase = true) }.takeIf { it >= 0 }
                                        ?: 3
                                    currentServerIndex = sr2Index
                                    useExoPlayer = false
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = NeonCyan,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isScrapingDirectStream || directScrapeSecondsRemaining > 0) "Connecting to high-speed stream..." else "Switching to Sr-2...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Loading media player...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            // Anime server selection fallback if needed
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                                    .fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Stream",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Select Streaming Server",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap any server below to play in HD",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                if (isAnime && (subServers.isNotEmpty() || dubServers.isNotEmpty())) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        val allSrvs = (subServers + dubServers).distinctBy { it.id.ifBlank { it.streamUrl } }
                                        items(allSrvs) { srv ->
                                            val isSel = selectedServer?.id == srv.id && selectedServer?.type == srv.type
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSel) NeonCyan.copy(alpha = 0.25f) else DeepSlate,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSel) NeonCyan else BorderColor
                                                ),
                                                modifier = Modifier
                                                    .clickable {
                                                        viewModel.selectAnikotoServer(srv)
                                                    }
                                                    .testTag("in_player_server_${srv.name}")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        text = srv.name,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isSel) NeonCyan else TextPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "(${srv.type.uppercase()})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSel) NeonCyan.copy(alpha = 0.8f) else TextSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LaunchedEffect(currentEmbedUrl) {
                    webViewRef?.let { webView ->
                        isLoading = true
                        hasError = false
                        if (currentEmbedUrl.contains("youtube.com/embed") || currentEmbedUrl.contains("youtube-nocookie.com/embed")) {
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <style>
                                        body { margin: 0; padding: 0; background-color: #000000; overflow: hidden; }
                                        .video-container { position: relative; width: 100%; height: 100vh; overflow: hidden; }
                                        .video-container iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
                                    </style>
                                </head>
                                <body>
                                    <div class="video-container">
                                        <iframe 
                                            src="$currentEmbedUrl" 
                                            frameborder="0" 
                                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                            referrerpolicy="strict-origin"
                                            allowfullscreen>
                                        </iframe>
                                    </div>
                                </body>
                                </html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                        } else {
                            val headers = mapOf("Referer" to currentEmbedUrl)
                            webView.loadUrl(currentEmbedUrl, headers)
                        }
                    }
                    // Prevent loading overlay getting stuck by auto-dismissing much faster (Light Speed)
                    kotlinx.coroutines.delay(800)
                    isLoading = false
                }
                
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            keepScreenOn = true
                            webViewRef = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
    
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            setBackgroundColor(AndroidColor.BLACK)
    
                            val webViewInstance = this
                            
                            settings.apply {
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                allowFileAccess = false
                                allowContentAccess = false
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                setRenderPriority(WebSettings.RenderPriority.HIGH)
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TD1A.220804.031) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36"
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }
                            }
                            
                            android.webkit.CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(webViewInstance, true)
                            }
                            
                            setDownloadListener { downloadUrl, webUserAgent, _, _, _ ->
                                try {
                                    val sanitizedTitle = title.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
                                    val fileName = if (type == "series") {
                                        "${sanitizedTitle}_S${currentSeason}E${currentEpisode}.mp4"
                                    } else {
                                        "${sanitizedTitle}.mp4"
                                    }
                                    val finalCookies = try {
                                        android.webkit.CookieManager.getInstance().getCookie(downloadUrl)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    com.example.download.MediaDownloader.downloadFile(
                                        context = ctx,
                                        url = downloadUrl,
                                        fileName = fileName,
                                        coroutineScope = scope,
                                        userAgent = webUserAgent,
                                        cookies = finalCookies,
                                        referer = this@apply.url ?: currentEmbedUrl
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    android.widget.Toast.makeText(ctx, "Download failed to start: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                               webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    if (newProgress >= 40) {
                                        isLoading = false
                                    }
                                }

                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    customView = view
                                    customViewCallback = callback
                                    isFullScreenVideo = true
                                    onFullScreenChange(true)
                                    
                                    // Hide status bars and navigation bars for absolute immersion
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        activity?.window?.insetsController?.let { controller ->
                                            controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                                            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                        }
                                    } else {
                                        @Suppress("DEPRECATION")
                                        activity?.window?.decorView?.systemUiVisibility = (
                                            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                        )
                                    }
                                    
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
     
                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    customView = null
                                    customViewCallback = null
                                    isFullScreenVideo = false
                                    onFullScreenChange(false)
                                    restoreSystemBars()
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
     
                                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                    val targetWebView = view ?: return true
                                    val popWebView = WebView(targetWebView.context)
                                    popWebView.settings.javaScriptEnabled = true
                                    popWebView.settings.domStorageEnabled = true
                                    popWebView.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                            val popUrl = request?.url?.toString()
                                            if (!popUrl.isNullOrBlank() && !popUrl.contains("about:blank")) {
                                                targetWebView.loadUrl(popUrl)
                                            }
                                            return true
                                        }
                                    }
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    if (transport != null) {
                                        transport.webView = popWebView
                                        resultMsg.sendToTarget()
                                    }
                                    return true
                                }
                            }
     
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    canGoBackState = view?.canGoBack() == true
                                    val fitCss = """
                                        javascript:(function() {
                                            var style = document.createElement('style');
                                            style.innerHTML = 'body { background: #000 !important; margin: 0; padding: 0; overflow: hidden !important; }';
                                            document.head.appendChild(style);
                                            
                                            // Prevent sandbox errors by assigning full permissions to iframes
                                            var iframes = document.getElementsByTagName('iframe');
                                            for (var i = 0; i < iframes.length; i++) {
                                                iframes[i].setAttribute('sandbox', 'allow-scripts allow-same-origin allow-presentation allow-downloads allow-forms allow-popups allow-popups-to-escape-sandbox allow-top-navigation-by-user-activation');
                                            }
                                            
                                            // Safely handle window.open without throwing errors or breaking media decoders
                                            window.open = function() { return { focus: function(){}, close: function(){}, closed: false }; };
                                            document.addEventListener('click', function(e) {
                                                var target = e.target.closest('a');
                                                if (target && target.target === '_blank') {
                                                    target.target = '_self';
                                                }
                                            }, true);
                                        })()
                                    """.trimIndent()
                                    view?.loadUrl(fitCss)
                                }
     
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val urlStr = request?.url?.toString() ?: return false
                                    val urlLower = urlStr.lowercase()
                                    if (urlLower.contains("youtube.com/watch") || urlLower.contains("youtu.be/")) {
                                        return true // Block redirecting or launching YouTube app
                                    }
                                    val adBlockList = listOf(
                                        "popunder", "propeller", "exoclick", "onclick", "popads", "syndication", 
                                        "googleads", "doubleclick", "analytics", "adsystem", 
                                        "chatbox", "bet365", "1xbet", "popup", "listats"
                                    )
                                    if (adBlockList.any { urlLower.contains(it) }) {
                                        return true
                                    }
     
                                    // Direct intercept for download APIs to ensure DownloadManager handles them perfectly
                                    if (urlLower.contains("/api/download/") || urlLower.contains("02moviedownloader.site/api/download")) {
                                        try {
                                            val sanitizedTitle = title.replace(Regex("[^A-Za-z0-9 ]"), "").replace(" ", "_")
                                            val fileName = if (type == "series") {
                                                "${sanitizedTitle}_S${currentSeason}E${currentEpisode}.mp4"
                                            } else {
                                                "${sanitizedTitle}.mp4"
                                            }
                                            val webUserAgent = view?.settings?.userAgentString
                                            val finalCookies = try {
                                                android.webkit.CookieManager.getInstance().getCookie(urlStr)
                                            } catch (e: Exception) {
                                                null
                                            }
                                            com.example.download.MediaDownloader.downloadFile(
                                                context = ctx,
                                                url = urlStr,
                                                fileName = fileName,
                                                coroutineScope = scope,
                                                userAgent = webUserAgent,
                                                cookies = finalCookies,
                                                referer = view?.url ?: currentEmbedUrl
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        return true // Handled
                                    }
     
                                    if (request?.isForMainFrame == false) {
                                        return false
                                    }
     
                                    val urlHost = try { android.net.Uri.parse(urlStr).host?.lowercase() ?: "" } catch(e: Exception) { "" }
                                    val currentHost = try { android.net.Uri.parse(currentEmbedUrl).host?.lowercase() ?: "" } catch(e: Exception) { "" }
                                    
                                    if (urlHost.contains(currentHost) || currentHost.contains(urlHost) || urlHost.isEmpty()) {
                                        return false
                                    }
     
                                    val allowedHosts = listOf(
                                        "vidsrc", "vixsrc", "sbs", "vidlink", "embed.su", "vidbinge", "multiembed", 
                                        "smashy", "2embed", "autoembed", "moviesapi", "google.com", "youtube", "ytimg", 
                                        "googlevideo", "vidplay", "megacloud", "cloudstream", "filemoon", "rcp",
                                        "vidnest", "vidrock", "vidzee", "anyembed", "videasy", "fmovies", "cine.su", 
                                        "02moviedownloader", "vsrc"
                                    )
                                    if (allowedHosts.any { urlHost.contains(it) }) {
                                        return false
                                    }
     
                                    // Block everything else as it's likely a popup or malicious redirect
                                    return true
                                }
    
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val urlStr = request?.url?.toString() ?: ""
                                    val urlStrLower = urlStr.lowercase()
                                    val adBlockList = listOf(
                                        "popunder", "propeller", "exoclick", "onclick", "popads", "syndication", 
                                        "googleads", "doubleclick", "analytics", "tracker", "adsystem", 
                                        "chatbox", "bet365", "1xbet", "popup", "listats"
                                    )
                                    for (ad in adBlockList) {
                                        if (urlStrLower.contains(ad)) {
                                            return android.webkit.WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                                        }
                                    }
    
                                    // Direct high-speed video link extraction (.m3u8, .mp4, stream endpoints, etc.)
                                    val isStreamPattern = urlStrLower.contains(".m3u8") || urlStrLower.contains(".mp4") || 
                                            urlStrLower.contains(".mkv") || urlStrLower.contains(".ts") ||
                                            urlStrLower.contains("master.m3u8") || urlStrLower.contains("index.m3u8") || 
                                            urlStrLower.contains("playlist.m3u8") || urlStrLower.contains("get_download") ||
                                            urlStrLower.contains("download_file") || urlStrLower.contains("/api/download/") ||
                                            urlStrLower.contains("/stream/video") || urlStrLower.contains("vidsrc.stream")
                                    
                                    if (isStreamPattern &&
                                        !urlStrLower.contains("googleads") && !urlStrLower.contains("analytics") && 
                                        !urlStrLower.contains("doubleclick") && !urlStrLower.contains("pixel") &&
                                        !urlStrLower.contains(".js") && !urlStrLower.contains(".css") &&
                                        !urlStrLower.contains("favicon") && !urlStrLower.contains(".png") && !urlStrLower.contains(".jpg")) {
                                        if (capturedVideoUrl != urlStr) {
                                            view?.post {
                                                capturedVideoUrl = urlStr
                                            }
                                        }
                                    }
    
                                    return super.shouldInterceptRequest(view, request)
                                }
                                   override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    if (request?.isForMainFrame == true) {
                                        val code = errorResponse?.statusCode ?: 0
                                        if (code == 404 || code >= 500) {
                                            // Let the server show its own 404/Error page instead of blocking UI
                                            isLoading = false
                                        }
                                    }
                                }
     
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        val errorCode = error?.errorCode ?: 0
                                        if (errorCode != -1 && errorCode != -3) {
                                            // Let the server show its own error page
                                            isLoading = false
                                        }
                                    }
                                }
    
                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    canGoBackState = view?.canGoBack() == true
                                }
                            }
    
                            val headers = mapOf("Referer" to currentEmbedUrl)
                            loadUrl(currentEmbedUrl, headers)
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                    },
                    modifier = Modifier.fillMaxSize()
                )
    
                // Loading Overlay inside frame
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpaceBlack.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Hey Almost Done...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
    
            }

            // Error State Overlay inside frame
            if (hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpaceBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = NeonMagenta,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Stream Unavailable",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                showServerDropdown = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Switch Embed Source (Servers)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // VIP Paywall Overlay inside player frame (Only locked if explicitly marked as premium in Admin Panel / Database)
            val isEpisodeLocked = remember(isVipUser, currentMediaItem) {
                if (isVipUser) {
                    false
                } else if (currentMediaItem?.isPremium == true) {
                    true
                } else {
                    false
                }
            }

            if (isEpisodeLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0B18).copy(alpha = 0.98f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isSeries) "Episode $currentEpisode is VIP Locked" else "VIP Premium Content",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isSeries) "Episode 1 was free to preview. Upgrade to VIP to unlock all remaining episodes and enjoy 4K streaming without ads!"
                            else "This content is reserved for VIP Premium members. Upgrade now to stream instantly!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showSubscriptionPlanModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upgrade to VIP ($3.99 / ৳399)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 3. Scrollable Section Below Player (Search Box, Season/Episode Selector & Related Movies / Anime)
        if (!isInPipMode && !isFullScreen) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            
            // Now Playing Info Card (Rich & Expandable)
            item {
                val playingItem = currentMediaItem
                    ?: allMediaItems.find { it.imdbId == imdbId || it.id == imdbId }
                    ?: allMediaItems.find { it.title.equals(title, ignoreCase = true) }
                if (playingItem != null) {
                    var posterUrlToUse by remember(playingItem.imageUrl) { mutableStateOf(playingItem.imageUrl) }
                    var hasPosterError by remember(posterUrlToUse) { mutableStateOf(false) }

                    LaunchedEffect(playingItem.title, isAnime) {
                        if (posterUrlToUse.isBlank() && isAnime) {
                            val enriched = withContext(Dispatchers.IO) {
                                com.example.scraper.AnimePosterEngine.getAnimePosterAndBanner(playingItem.title)
                            }
                            if (enriched != null && enriched.posterUrl.isNotBlank()) {
                                posterUrlToUse = enriched.posterUrl
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(DeepSlate, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Small Poster on Left + Trailer Button below poster
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(80.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF1E1B4B), SpaceBlack)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (posterUrlToUse.isNotBlank() && !hasPosterError) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(posterUrlToUse)
                                                .crossfade(true)
                                                .listener(
                                                    onError = { _, _ ->
                                                        hasPosterError = true
                                                    }
                                                )
                                                .build(),
                                            contentDescription = "Poster",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        // Fallback decorative anime placeholder
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isAnime) Icons.Default.AutoAwesome else Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = NeonMagenta,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = playingItem.title.take(12),
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        val ytId = youtubeTrailerId
                                        val targetUrl = if (!ytId.isNullOrBlank()) {
                                            "https://www.youtube.com/watch?v=$ytId"
                                        } else {
                                            "https://www.youtube.com/results?search_query=" + android.net.Uri.encode("$title official trailer")
                                        }
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                                setPackage("com.google.android.youtube")
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(fallbackIntent)
                                            } catch (ex: Exception) {
                                                // ignore
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Trailer",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Trailer",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Details on Right
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playingItem.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    ),
                                    color = NeonCyan,
                                    lineHeight = 24.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "★ ${playingItem.rating} TMDB",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFFFD700)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val durationText = remember(mediaDetailState, isSeries) {
                                            if (isSeries) {
                                                val seasons = mediaDetailState?.number_of_seasons ?: 0
                                                val episodes = mediaDetailState?.number_of_episodes ?: 0
                                                if (seasons > 0) "$seasons Seasons" else if (episodes > 0) "$episodes Episodes" else "45 mins"
                                            } else {
                                                val runtime = mediaDetailState?.runtime ?: 0
                                                if (runtime > 0) "$runtime mins" else "120 mins"
                                            }
                                        }
                                        Text(
                                            text = durationText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                if (isSeries) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Currently Watching: S$currentSeason E$currentEpisode",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonMagenta
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Expandable Content
                        AnimatedVisibility(visible = isDescriptionExpanded) {
                            Column {
                                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                                
                                Text(
                                    text = "Synopsis",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = NeonCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = playingItem.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )

                                if (castList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Top Cast",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = NeonCyan
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(end = 16.dp)
                                    ) {
                                        items(castList) { actor ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.width(70.dp)
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data("https://image.tmdb.org/t/p/w200${actor.profilePath}")
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = actor.name,
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(CircleShape)
                                                        .background(SpaceBlack),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = actor.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                                Text(
                                                    text = actor.character,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Details",
                            tint = NeonCyan,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // Stream Servers Multi-Selection Section (Anime SUB/DUB & High-Speed Direct/Multi Servers)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(DeepSlate, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Servers",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Stream Servers",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        if (isFetchingServers || isDeepScraping) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "Scanning servers...",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        } else if (isMainSelected && selectedVidnestServerKey == null && selectedServer == null) {
                            Surface(
                                color = NeonMagenta.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "MAIN: Fastest Direct",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonMagenta,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else if (selectedVidnestServerKey != null) {
                            val verifiedMatch = verifiedServers.find { it.key == selectedVidnestServerKey }
                            val currentVidnestServer = com.example.scraper.VidnestNativeScraper.PROVIDERS.find { it.key == selectedVidnestServerKey }
                            val serverLabel = verifiedMatch?.name ?: when (selectedVidnestServerKey) {
                                "beta" -> "Beta"
                                "sigma" -> "Sigma"
                                "vidlink_direct" -> "VidLink (Pro)"
                                "vidsrc_direct" -> "VidSrc (Multi)"
                                "autoembed_direct" -> "AutoEmbed"
                                "vidrock_direct" -> "ZOZO"
                                "delta" -> "HINDI"
                                "prime" -> "Prime"
                                "hexa" -> "Hexa Prime"
                                "gama" -> "Gamma"
                                "alfa" -> "Alfa"
                                "catflix" -> "Catflix"
                                "zeta" -> "Zeta"
                                "ophim" -> "Ophim"
                                "filxer" -> "HM VIP"
                                else -> currentVidnestServer?.displayName ?: "Server A"
                            }
                            val badgeColor = if (verifiedMatch != null) Color(verifiedMatch.accentColorHex) else when (selectedVidnestServerKey) {
                                "beta" -> NeonCyan
                                "sigma" -> Color(0xFF3F51B5)
                                "vidlink_direct" -> Color(0xFF6C5CE7)
                                "vidrock_direct" -> Color(0xFF00E5FF)
                                "delta" -> Color(0xFFFF9800)
                                else -> Color(0xFFFF9800)
                            }
                            Surface(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "SERVER: ${serverLabel.uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else if (selectedServer != null) {
                            Surface(
                                color = (if (selectedServer?.type?.lowercase() == "dub") NeonPurple else NeonCyan).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${selectedServer?.type?.uppercase() ?: "SUB"}: ${selectedServer?.name}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedServer?.type?.lowercase() == "dub") NeonPurple else NeonCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                        // Line 1: SUB Servers
                        if (isAnime && subServers.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NeonCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = "SUB",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "SUB",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = NeonCyan
                                        )
                                    }
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(subServers) { srv ->
                                        val isSelected = selectedVidnestServerKey == null && selectedServer?.linkId == srv.linkId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedVidnestServerKey = null
                                                viewModel.selectAnikotoServer(srv, currentEpisode)
                                                scope.launch(Dispatchers.IO) {
                                                    withContext(Dispatchers.Main) {
                                                        isLoading = true
                                                        hasError = false
                                                    }
                                                    val extracted = if (srv.streamUrl.isNotBlank()) {
                                                        com.example.scraper.ScrapedStreamResult(
                                                            streamUrl = srv.streamUrl,
                                                            headers = mapOf(
                                                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                                                "Referer" to if (srv.referer.isNotBlank()) srv.referer else "https://anikoto.cz/",
                                                                "Origin" to "https://anikoto.cz"
                                                            ),
                                                            referer = if (srv.referer.isNotBlank()) srv.referer else "https://anikoto.cz/",
                                                            subtitles = srv.tracks
                                                        )
                                                    } else {
                                                        com.example.scraper.UnifiedStreamManager.getStream(
                                                            context = context,
                                                            title = title,
                                                            tmdbId = imdbId,
                                                            isTv = isSeries,
                                                            season = currentSeason,
                                                            episode = currentEpisode,
                                                            isAnime = true,
                                                            audioType = srv.type.lowercase(),
                                                            requestedServerKey = srv.id
                                                        )
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        if (extracted != null && extracted.streamUrl.isNotBlank()) {
                                                            capturedVideoUrl = extracted.streamUrl
                                                            customScrapedHeaders = extracted.headers
                                                            if (extracted.subtitles.isNotEmpty()) {
                                                                activeSubtitles = extracted.subtitles
                                                            }
                                                            useExoPlayer = true
                                                            isLoading = false
                                                            hasError = false
                                                        } else {
                                                            isLoading = false
                                                            hasError = true
                                                        }
                                                    }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = srv.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NeonCyan,
                                                selectedLabelColor = Color.Black,
                                                containerColor = SpaceBlack,
                                                labelColor = TextPrimary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = BorderColor,
                                                selectedBorderColor = NeonCyan
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Line 2: DUB Servers
                        if (isAnime && dubServers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NeonPurple.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "DUB",
                                            tint = NeonPurple,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "DUB",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = NeonPurple
                                        )
                                    }
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(dubServers) { srv ->
                                        val isSelected = selectedVidnestServerKey == null && selectedServer?.linkId == srv.linkId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedVidnestServerKey = null
                                                viewModel.selectAnikotoServer(srv, currentEpisode)
                                                scope.launch(Dispatchers.IO) {
                                                    withContext(Dispatchers.Main) {
                                                        isLoading = true
                                                        hasError = false
                                                    }
                                                    val extracted = if (srv.streamUrl.isNotBlank()) {
                                                        com.example.scraper.ScrapedStreamResult(
                                                            streamUrl = srv.streamUrl,
                                                            headers = mapOf(
                                                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                                                "Referer" to if (srv.referer.isNotBlank()) srv.referer else "https://anikoto.cz/",
                                                                "Origin" to "https://anikoto.cz"
                                                            ),
                                                            referer = if (srv.referer.isNotBlank()) srv.referer else "https://anikoto.cz/",
                                                            subtitles = srv.tracks
                                                        )
                                                    } else {
                                                        com.example.scraper.UnifiedStreamManager.getStream(
                                                            context = context,
                                                            title = title,
                                                            tmdbId = imdbId,
                                                            isTv = isSeries,
                                                            season = currentSeason,
                                                            episode = currentEpisode,
                                                            isAnime = true,
                                                            audioType = srv.type.lowercase(),
                                                            requestedServerKey = srv.id
                                                        )
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        if (extracted != null && extracted.streamUrl.isNotBlank()) {
                                                            capturedVideoUrl = extracted.streamUrl
                                                            customScrapedHeaders = extracted.headers
                                                            if (extracted.subtitles.isNotEmpty()) {
                                                                activeSubtitles = extracted.subtitles
                                                            }
                                                            useExoPlayer = true
                                                            isLoading = false
                                                            hasError = false
                                                        } else {
                                                            isLoading = false
                                                            hasError = true
                                                        }
                                                    }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = srv.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NeonPurple,
                                                selectedLabelColor = Color.White,
                                                containerColor = SpaceBlack,
                                                labelColor = TextPrimary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = BorderColor,
                                                selectedBorderColor = NeonPurple
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Line 3: Multi-Source Direct Stream Servers (Vidnest Providers: Beta, Sigma, VidLink, ZOZO, Prime, Hexa, etc.)
                        if (!isAnime || subServers.isEmpty() && dubServers.isEmpty() || verifiedServers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = "MULTI",
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "MULTI",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFFF9800)
                                        )
                                    }
                                }

                                val serverChipList = listOf(
                                    Triple("parallel", "Parallel", NeonMagenta),
                                    Triple("filxer", "Flixer", Color(0xFFFF4081)),
                                    Triple("beta", "Beta", Color(0xFF00E5FF)),
                                    Triple("delta", "delta", Color(0xFF9C27B0)),
                                    Triple("zeta", "Zeta", Color(0xFFFF9800)),
                                    Triple("ophim", "Ophim", Color(0xFF00B0FF)),
                                    Triple("alfa", "Alfa", Color(0xFF4CAF50)),
                                    Triple("gama", "Gamma", Color(0xFFFF5722)),
                                    Triple("catflix", "Catflix", Color(0xFFFFEB3B)),
                                    Triple("sigma", "Sigma", Color(0xFF3F51B5)),
                                    Triple("hexa", "Hexa Prime", Color(0xFF009688)),
                                    Triple("lamda", "Lamda", Color(0xFFE91E63)),
                                    Triple("vidrock_direct", "ZOZO", Color(0xFF00E5FF))
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(serverChipList) { item ->
                                        val (key, label, accentColor) = item
                                        val isSelected = if (key == "parallel") isMainSelected else (!isMainSelected && selectedVidnestServerKey == key)
                                        val isVerified = verifiedServers.any { it.key == key && it.result.streamUrl.isNotBlank() }

                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (key == "parallel") {
                                                    isMainSelected = true
                                                    selectedVidnestServerKey = null
                                                    viewModel.selectAnikotoServer(null)
                                                    if (mainScrapedVideoUrl != null) {
                                                        capturedVideoUrl = mainScrapedVideoUrl
                                                        customScrapedHeaders = mainScrapedHeaders ?: emptyMap()
                                                        useExoPlayer = true
                                                    } else {
                                                        scope.launch(Dispatchers.IO) {
                                                            withContext(Dispatchers.Main) {
                                                                isLoading = true
                                                                hasError = false
                                                            }
                                                            val res = com.example.scraper.UnifiedStreamManager.raceFastestServerStream(
                                                                context = context,
                                                                tmdbId = imdbId,
                                                                title = title,
                                                                isTv = isSeries,
                                                                season = currentSeason,
                                                                episode = currentEpisode
                                                            )?.result
                                                            withContext(Dispatchers.Main) {
                                                                if (res != null && res.streamUrl.isNotBlank()) {
                                                                    capturedVideoUrl = res.streamUrl
                                                                    customScrapedHeaders = res.headers
                                                                    mainScrapedVideoUrl = res.streamUrl
                                                                    mainScrapedHeaders = res.headers
                                                                    activeSubtitles = res.subtitles
                                                                    useExoPlayer = true
                                                                    isLoading = false
                                                                    hasError = false
                                                                } else {
                                                                    isLoading = false
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    isMainSelected = false
                                                    selectedVidnestServerKey = key
                                                    viewModel.selectAnikotoServer(null)
                                                    val directVerified = com.example.scraper.UnifiedStreamManager.getVerifiedServerStream(imdbId, currentSeason, currentEpisode, key)
                                                    if (directVerified != null && directVerified.streamUrl.isNotBlank()) {
                                                        capturedVideoUrl = directVerified.streamUrl
                                                        customScrapedHeaders = directVerified.headers
                                                        mainScrapedVideoUrl = directVerified.streamUrl
                                                        mainScrapedHeaders = directVerified.headers
                                                        if (directVerified.subtitles.isNotEmpty()) {
                                                            activeSubtitles = directVerified.subtitles
                                                        }
                                                        useExoPlayer = true
                                                        isLoading = false
                                                        hasError = false
                                                    } else {
                                                         scope.launch(Dispatchers.IO) {
                                                            withContext(Dispatchers.Main) {
                                                                isLoading = true
                                                                isScrapingDirectStream = true
                                                                hasError = false
                                                            }
                                                            val extracted = when (key) {
                                                                "vidlink_direct" -> com.example.scraper.VidLinkNativeScraper.extractStream(
                                                                    tmdbId = imdbId, isTv = isSeries, season = currentSeason, episode = currentEpisode
                                                                )
                                                                "vidsrc_direct" -> com.example.scraper.VidSrcNativeScraper.extractStream(
                                                                    tmdbId = imdbId, isTv = isSeries, season = currentSeason, episode = currentEpisode
                                                                )
                                                                "autoembed_direct" -> com.example.scraper.AutoEmbedNativeScraper.extractStream(
                                                                    tmdbId = imdbId, isTv = isSeries, season = currentSeason, episode = currentEpisode
                                                                )
                                                                "vidrock_direct" -> com.example.scraper.VidrockNativeScraper.extractStream(
                                                                    tmdbId = imdbId, isTv = isSeries, season = currentSeason, episode = currentEpisode
                                                                )
                                                                else -> com.example.scraper.VidnestNativeScraper.extractStreamFromProvider(
                                                                    providerKey = key,
                                                                    tmdbId = imdbId,
                                                                    isTv = isSeries,
                                                                    season = currentSeason,
                                                                    episode = currentEpisode
                                                                )
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                isLoading = false
                                                                isScrapingDirectStream = false
                                                                if (extracted != null && extracted.streamUrl.isNotBlank()) {
                                                                    capturedVideoUrl = extracted.streamUrl
                                                                    customScrapedHeaders = extracted.headers
                                                                    if (extracted.subtitles.isNotEmpty()) {
                                                                        activeSubtitles = extracted.subtitles
                                                                    }
                                                                    useExoPlayer = true
                                                                    hasError = false
                                                                } else {
                                                                    // Fallback: race fastest server across cloud providers
                                                                    scope.launch(Dispatchers.IO) {
                                                                        val fallback = com.example.scraper.UnifiedStreamManager.raceFastestServerStream(
                                                                            context = context,
                                                                            tmdbId = imdbId,
                                                                            title = title,
                                                                            isTv = isSeries,
                                                                            season = currentSeason,
                                                                            episode = currentEpisode,
                                                                            preferredServerKey = "fastest_auto"
                                                                        )?.result
                                                                        withContext(Dispatchers.Main) {
                                                                            if (fallback != null && fallback.streamUrl.isNotBlank()) {
                                                                                capturedVideoUrl = fallback.streamUrl
                                                                                customScrapedHeaders = fallback.headers
                                                                                useExoPlayer = true
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (key == "parallel") {
                                                        Text("⚡ ", fontSize = 11.sp)
                                                    } else if (isVerified) {
                                                        Text("● ", color = Color(0xFF00E676), fontSize = 10.sp)
                                                    }
                                                    Text(
                                                        text = label,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accentColor,
                                                selectedLabelColor = if (accentColor == Color(0xFF00E5FF) || accentColor == Color(0xFFFF9800)) Color.Black else Color.White,
                                                containerColor = SpaceBlack,
                                                labelColor = TextPrimary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = BorderColor,
                                                selectedBorderColor = accentColor
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

            // Season & Episode Selector for TV Series & Anime
            if (isSeries) {
                item {
                    if (showJumpDialog) {
                        var inputSeasonText by remember { mutableStateOf(currentSeason.toString()) }
                        var inputEpText by remember { mutableStateOf(currentEpisode.toString()) }
                        val rawEpisodesList = if (isAnime && anikotoEpisodes.isNotEmpty()) {
                            anikotoEpisodes.map { it.number }.sorted()
                        } else {
                            seasonEpisodeMap[currentSeason] ?: emptyList()
                        }
                        val maxKnownEp = if (rawEpisodesList.isNotEmpty()) rawEpisodesList.maxOrNull() ?: 1 else 1500

                        Dialog(onDismissRequest = { showJumpDialog = false }) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(NeonCyan, NeonPurple))),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "⚡ Quick Jump (Season & Ep)",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimary
                                            )
                                        }
                                        IconButton(
                                            onClick = { showJumpDialog = false },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Select Season and Episode number to watch immediately.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quick Season Selector Chips inside Dialog
                                    Text(
                                        text = "Quick Season:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val dialogSeasons = if (isAnime && anikotoSeasons.isNotEmpty()) {
                                            anikotoSeasons.map { it.number }.sorted()
                                        } else {
                                            val keys = seasonEpisodeMap.keys.sorted()
                                            if (keys.size > 1) keys else (1..maxOf(4, currentSeason)).toList()
                                        }
                                        items(dialogSeasons) { sNum ->
                                            val isSel = (inputSeasonText.toIntOrNull() ?: currentSeason) == sNum
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSel) NeonCyan else SpaceBlack,
                                                border = BorderStroke(1.dp, if (isSel) NeonCyan else BorderColor),
                                                modifier = Modifier.clickable {
                                                    inputSeasonText = sNum.toString()
                                                }
                                            ) {
                                                Text(
                                                    text = "Season $sNum",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) Color.Black else TextPrimary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Season & Episode Input Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = inputSeasonText,
                                            onValueChange = { str ->
                                                if (str.all { it.isDigit() } && str.length <= 3) {
                                                    inputSeasonText = str
                                                }
                                            },
                                            label = { Text("Season") },
                                            placeholder = { Text("1") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonCyan,
                                                unfocusedBorderColor = BorderColor,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary,
                                                cursorColor = NeonCyan
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        OutlinedTextField(
                                            value = inputEpText,
                                            onValueChange = { str ->
                                                if (str.all { it.isDigit() } && str.length <= 5) {
                                                    inputEpText = str
                                                }
                                            },
                                            label = { Text("Episode (1-${maxOf(maxKnownEp, 1200)})") },
                                            placeholder = { Text("1") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(onDone = {
                                                val sNum = inputSeasonText.toIntOrNull() ?: currentSeason
                                                val epNum = inputEpText.toIntOrNull() ?: currentEpisode
                                                if (sNum > 0 && epNum > 0) {
                                                    currentSeason = sNum
                                                    currentEpisode = epNum
                                                    isLoading = true
                                                    hasError = false
                                                    showJumpDialog = false
                                                    if (isAnime) {
                                                        val tempItem = currentMediaItem ?: MediaItem(
                                                            id = imdbId,
                                                            imdbId = imdbId,
                                                            title = title,
                                                            category = "Anime",
                                                            imageUrl = "",
                                                            type = type,
                                                            year = "2024"
                                                        )
                                                        viewModel.fetchAnikotoMediaData(tempItem, sNum)
                                                        viewModel.fetchAnikotoServers(tempItem, sNum, epNum)
                                                    }
                                                }
                                            }),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonCyan,
                                                unfocusedBorderColor = BorderColor,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary,
                                                cursorColor = NeonCyan
                                            ),
                                            modifier = Modifier.weight(1.5f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Fast increment / decrement step chips for Episode
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val curVal = inputEpText.toIntOrNull() ?: currentEpisode
                                        listOf(-50, -10, +10, +50).forEach { delta ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = SpaceBlack,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.clickable {
                                                    val newVal = maxOf(1, curVal + delta)
                                                    inputEpText = newVal.toString()
                                                }
                                            ) {
                                                Text(
                                                    text = if (delta > 0) "+$delta" else "$delta",
                                                    color = NeonCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val sNum = inputSeasonText.toIntOrNull() ?: currentSeason
                                            val epNum = inputEpText.toIntOrNull() ?: currentEpisode
                                            if (sNum > 0 && epNum > 0) {
                                                currentSeason = sNum
                                                currentEpisode = epNum
                                                isLoading = true
                                                hasError = false
                                                showJumpDialog = false
                                                if (isAnime) {
                                                    val tempItem = currentMediaItem ?: MediaItem(
                                                        id = imdbId,
                                                        imdbId = imdbId,
                                                        title = title,
                                                        category = "Anime",
                                                        imageUrl = "",
                                                        type = type,
                                                        year = "2024"
                                                    )
                                                    viewModel.fetchAnikotoMediaData(tempItem, sNum)
                                                    viewModel.fetchAnikotoServers(tempItem, sNum, epNum)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Watch S${inputSeasonText.ifBlank { currentSeason.toString() }} : E${inputEpText.ifBlank { currentEpisode.toString() }} Now",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(DeepSlate, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Direct Jump button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NeonCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, NeonCyan),
                                    modifier = Modifier.clickable { showJumpDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = "Quick Jump",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Change Season/Ep",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                }

                                if (isFetchingMeta || isAnimeLoading) {
                                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SpaceBlack,
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier.clickable { showJumpDialog = true }
                                    ) {
                                        Text(
                                            text = "S$currentSeason : E$currentEpisode",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = NeonMagenta,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isFetchingMeta && !isAnime) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Fetching official seasons & episodes...",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        } else {
                            // Compute Seasons List
                            val seasonsList = if (isAnime && anikotoSeasons.isNotEmpty()) {
                                anikotoSeasons.map { it.number }.sorted()
                            } else {
                                val keys = seasonEpisodeMap.keys.sorted()
                                if (keys.size > 1) keys else (1..maxOf(4, currentSeason)).toList()
                            }

                            // Compute Episodes List
                            val rawEpisodesList = if (isAnime && anikotoEpisodes.isNotEmpty()) {
                                anikotoEpisodes.map { it.number }.sorted()
                            } else {
                                seasonEpisodeMap[currentSeason] ?: emptyList()
                            }
                            
                            val episodesList = if (rawEpisodesList.isNotEmpty()) {
                                if (currentEpisode > rawEpisodesList.last()) {
                                    (1..currentEpisode).toList()
                                } else {
                                    rawEpisodesList
                                }
                            } else {
                                (1..maxOf(currentEpisode, 24)).toList()
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Prev / Next Episode Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (currentEpisode > 1) {
                                            currentEpisode -= 1
                                            isLoading = true
                                            hasError = false
                                            if (isAnime) {
                                                val tempItem = currentMediaItem ?: MediaItem(
                                                    id = imdbId,
                                                    imdbId = imdbId,
                                                    title = title,
                                                    category = "Anime",
                                                    imageUrl = "",
                                                    type = type,
                                                    year = "2024"
                                                )
                                                viewModel.fetchAnikotoServers(tempItem, currentSeason, currentEpisode)
                                            }
                                        }
                                    },
                                    enabled = currentEpisode > 1,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpaceBlack,
                                        disabledContainerColor = SpaceBlack.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(1.dp, if (currentEpisode > 1) NeonCyan.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Prev", tint = if (currentEpisode > 1) NeonCyan else TextSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Ep ${maxOf(1, currentEpisode - 1)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentEpisode > 1) TextPrimary else TextSecondary
                                    )
                                }

                                Text(
                                    text = "Season $currentSeason • Ep $currentEpisode",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )

                                Button(
                                    onClick = {
                                        currentEpisode += 1
                                        isLoading = true
                                        hasError = false
                                        if (isAnime) {
                                            val tempItem = currentMediaItem ?: MediaItem(
                                                id = imdbId,
                                                imdbId = imdbId,
                                                title = title,
                                                category = "Anime",
                                                imageUrl = "",
                                                type = type,
                                                year = "2024"
                                            )
                                            viewModel.fetchAnikotoServers(tempItem, currentSeason, currentEpisode)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SpaceBlack),
                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "Ep ${currentEpisode + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Season Selector Section (Always Available)
                            Text(
                                text = "Select Season (${seasonsList.size} Available):",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(seasonsList) { s ->
                                    val seasonTitle = if (isAnime && anikotoSeasons.isNotEmpty()) {
                                        anikotoSeasons.find { it.number == s }?.title ?: "Season $s"
                                    } else {
                                        "Season $s"
                                    }
                                    FilterChip(
                                        selected = currentSeason == s,
                                        onClick = {
                                            currentSeason = s
                                            currentEpisode = 1
                                            isLoading = true
                                            hasError = false
                                            if (isAnime) {
                                                val tempItem = currentMediaItem ?: MediaItem(
                                                    id = imdbId,
                                                    imdbId = imdbId,
                                                    title = title,
                                                    category = "Anime",
                                                    imageUrl = "",
                                                    type = type,
                                                    year = "2024"
                                                )
                                                viewModel.fetchAnikotoMediaData(tempItem, s)
                                                viewModel.fetchAnikotoServers(tempItem, s, 1)
                                            }
                                        },
                                        label = { Text(seasonTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan,
                                            selectedLabelColor = Color.Black,
                                            containerColor = SpaceBlack,
                                            labelColor = TextPrimary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = currentSeason == s,
                                            borderColor = BorderColor,
                                            selectedBorderColor = NeonCyan
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            if (episodesList.isNotEmpty()) {
                                val chunkSize = 25
                                val hasManyEpisodes = episodesList.size > 25

                                // Split into 25-episode batches/ranges (e.g. 1-25, 26-50, etc.)
                                val ranges = remember(episodesList.size) {
                                    if (hasManyEpisodes) {
                                        episodesList.chunked(chunkSize).map { chunk ->
                                            Pair(chunk.first(), chunk.last())
                                        }
                                    } else {
                                        emptyList()
                                    }
                                }

                                var selectedRangeIndex by remember(currentSeason, episodesList.size) {
                                    val initialIdx = if (ranges.isNotEmpty()) {
                                        val foundIdx = ranges.indexOfFirst { currentEpisode in it.first..it.second }
                                        if (foundIdx >= 0) foundIdx else ((currentEpisode - 1) / chunkSize).coerceIn(0, ranges.size - 1)
                                    } else 0
                                    mutableIntStateOf(initialIdx)
                                }

                                // Auto sync selectedRangeIndex when currentEpisode changes
                                LaunchedEffect(currentEpisode, ranges.size) {
                                    if (ranges.isNotEmpty()) {
                                        val foundIdx = ranges.indexOfFirst { currentEpisode in it.first..it.second }
                                        if (foundIdx >= 0 && foundIdx != selectedRangeIndex) {
                                            selectedRangeIndex = foundIdx
                                        }
                                    }
                                }

                                val rangeListState = rememberLazyListState()
                                LaunchedEffect(selectedRangeIndex) {
                                    if (ranges.isNotEmpty() && selectedRangeIndex in ranges.indices) {
                                        rangeListState.animateScrollToItem(selectedRangeIndex)
                                    }
                                }

                                // If series has many episodes, show range tabs (e.g. 1-25, 26-50, etc.)
                                if (hasManyEpisodes && ranges.isNotEmpty()) {
                                    Text(
                                        text = "Episode Batches (${episodesList.size} Total Episodes):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        state = rangeListState,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        itemsIndexed(ranges) { idx, range ->
                                            val isRangeSelected = selectedRangeIndex == idx
                                            val isCurrentInThisRange = currentEpisode in range.first..range.second
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isRangeSelected) NeonCyan else SpaceBlack,
                                                border = BorderStroke(1.dp, if (isCurrentInThisRange && !isRangeSelected) NeonMagenta else if (isRangeSelected) NeonCyan else BorderColor),
                                                modifier = Modifier.clickable {
                                                    selectedRangeIndex = idx
                                                }
                                            ) {
                                                Text(
                                                    text = "${range.first} - ${range.second}",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isRangeSelected || isCurrentInThisRange) FontWeight.ExtraBold else FontWeight.Medium,
                                                    color = if (isRangeSelected) Color.Black else if (isCurrentInThisRange) NeonMagenta else TextPrimary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Visible episodes in current range (or all if < 25)
                                val visibleEpisodes = if (hasManyEpisodes && ranges.isNotEmpty() && selectedRangeIndex in ranges.indices) {
                                    val r = ranges[selectedRangeIndex]
                                    episodesList.filter { it in r.first..r.second }
                                } else {
                                    episodesList
                                }

                                val epListState = rememberLazyListState()
                                LaunchedEffect(currentEpisode, visibleEpisodes) {
                                    val idx = visibleEpisodes.indexOf(currentEpisode)
                                    if (idx >= 0) {
                                        epListState.animateScrollToItem(maxOf(0, idx - 2))
                                    }
                                }

                                // Episode Selector Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (hasManyEpisodes && ranges.isNotEmpty() && selectedRangeIndex in ranges.indices) {
                                            "Episodes ${ranges[selectedRangeIndex].first} - ${ranges[selectedRangeIndex].second}"
                                        } else {
                                            "Select Episode (${episodesList.size} Episodes)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )

                                    Text(
                                        text = "Ep $currentEpisode Selected",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    state = epListState,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(visibleEpisodes) { ep ->
                                        val isEpLocked = !isVipUser && (currentMediaItem?.isPremium == true)
                                        val epTitle = if (isAnime && anikotoEpisodes.isNotEmpty()) {
                                            anikotoEpisodes.find { it.number == ep }?.title
                                        } else null
                                        val labelText = if (isEpLocked) {
                                            "🔒 Ep $ep"
                                        } else if (!epTitle.isNullOrBlank() && !epTitle.equals("Episode $ep", ignoreCase = true) && epTitle.length <= 16) {
                                            "Ep $ep: $epTitle"
                                        } else {
                                            "Ep $ep"
                                        }
                                        FilterChip(
                                            selected = currentEpisode == ep,
                                            onClick = {
                                                if (currentEpisode != ep) {
                                                    currentEpisode = ep
                                                    if (isEpLocked) {
                                                        showSubscriptionPlanModal = true
                                                    } else {
                                                        isLoading = true
                                                        hasError = false
                                                        if (isAnime) {
                                                            viewModel.selectAnikotoServer(null)
                                                            val tempItem = currentMediaItem ?: MediaItem(
                                                                id = imdbId,
                                                                imdbId = imdbId,
                                                                title = title,
                                                                category = "Anime",
                                                                imageUrl = "",
                                                                type = type,
                                                                year = "2024"
                                                            )
                                                            viewModel.fetchAnikotoServers(tempItem, currentSeason, ep)
                                                        }
                                                    }
                                                }
                                            },
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isEpLocked) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = "VIP Locked",
                                                            tint = if (currentEpisode == ep) Color.Black else Color(0xFFFFD700),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                    }
                                                    Text(
                                                        text = if (isEpLocked) "Ep $ep" else labelText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (currentEpisode == ep) Color.Black else if (isEpLocked) Color(0xFFFFD700) else TextPrimary
                                                    )
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = if (isEpLocked) Color(0xFFFFD700) else NeonCyan,
                                                selectedLabelColor = Color.Black,
                                                containerColor = if (isEpLocked) Color(0xFF2C1A30) else SpaceBlack,
                                                labelColor = if (isEpLocked) Color(0xFFFFD700) else TextPrimary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = currentEpisode == ep,
                                                borderColor = if (isEpLocked) Color(0xFFFFD700).copy(alpha = 0.5f) else BorderColor,
                                                selectedBorderColor = if (isEpLocked) Color(0xFFFFD700) else NeonCyan
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2-Button Toggle: Recommend / Related vs Comments
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(DeepSlate, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Recommend Button
                    Button(
                        onClick = { selectedBottomTab = "recommend" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedBottomTab == "recommend") NeonCyan else Color.Transparent,
                            contentColor = if (selectedBottomTab == "recommend") Color.Black else TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        elevation = null
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Recommend",
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedBottomTab == "recommend") Color.Black else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recommend",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Comments Button
                    Button(
                        onClick = { selectedBottomTab = "comments" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedBottomTab == "comments") NeonCyan else Color.Transparent,
                            contentColor = if (selectedBottomTab == "comments") Color.Black else TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        elevation = null
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Comment,
                                contentDescription = "Comments",
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedBottomTab == "comments") Color.Black else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Comments",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (selectedBottomTab == "comments") {
                // Real-time Community Comments & Rating Section (Firebase Firestore + Room persistence)
                item {
                    MediaCommentsAndRatingSection(
                        imdbId = imdbId,
                        title = title,
                        viewModel = viewModel,
                        userProfile = userProfile
                    )
                }
            } else {
                val isSearching = localSearchQuery.isNotBlank()
                val displayList = if (isSearching) searchResultsList else filteredRelated

                // Category Header / Search Results Header
                item {
                    val isAnimePlaying = type.equals("anime", ignoreCase = true) || (currentMediaItemForRelated?.category?.contains("anime", ignoreCase = true) == true && currentMediaItemForRelated?.category?.equals("Anime & Series", ignoreCase = true) != true)
                    val isBanglaPlaying = currentMediaItemForRelated?.category?.equals("Bangla Cinema & Natok", ignoreCase = true) == true
                    val isMoviePlaying = (!isSeries && !isAnimePlaying) || currentMediaItemForRelated?.type?.equals("movie", ignoreCase = true) == true
                    
                    val headerTitle = if (isSearching) {
                        "🔍 Search Results for \"$localSearchQuery\" (${displayList.size})"
                    } else if (isBanglaPlaying) {
                        "🎬 Related Bangla Content"
                    } else if (isAnimePlaying) {
                        "🎬 Related Anime"
                    } else if (isMoviePlaying) {
                        "🎬 Related Movies"
                    } else {
                        "🎬 Related TV Shows"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSearching) NeonCyan else TextPrimary
                        )
                        if (isSearchingOnline) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NeonCyan,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                if (isSearching && displayList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSearchingOnline) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(28.dp))
                                    Text(
                                        text = "Searching across Movies, TV Series & Anime...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            } else {
                                Text(
                                    text = "No results found for \"$localSearchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    // Grid of Media Items
                    items(displayList.chunked(3)) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowItems.forEach { item ->
                                RelatedMediaCard(
                                    item = item,
                                    onClick = { selectedDetailItem = item },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        }
      }
    }

        if (showServerDropdown) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            var isExpanded by remember(showServerDropdown) { mutableStateOf(false) }
            val gradientColors = remember {
                listOf(
                    listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)), // Sunset Magenta-Orange
                    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),                   // Electric Blue
                    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),                   // Neon Mint
                    listOf(Color(0xFFFF512F), Color(0xFFDD2476)),                   // Hot Pink / Red
                    listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)),                   // Cyberpunk Pink-Blue
                    listOf(Color(0xFFDA22FF), Color(0xFF9733EE))                    // Purple Magic
                ).map { Brush.linearGradient(it) }
            }

            ModalBottomSheet(
                onDismissRequest = { showServerDropdown = false },
                containerColor = if (isDark) Color(0xFF13131A) else Color.White,
                scrimColor = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "SELECT STREAMING SERVER",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Text(
                                    text = "Tap any server to switch stream source",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            onClick = { showServerDropdown = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isDark) Color.LightGray else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val visibleServers = if (embedServers.size > 15 && !isExpanded) {
                        embedServers.take(15)
                    } else {
                        embedServers
                    }

                    val serverRows = visibleServers.mapIndexed { index, pair -> index to pair }.chunked(4)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        serverRows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowItems.forEach { (index, serverPair) ->
                                    val isSelected = (index == (currentServerIndex % embedServers.size))
                                    val isMainServer = index in 0..4
                                    val bgColor = if (isSelected) Color(0xFF4CAF50) else Color.DarkGray.copy(alpha = 0.5f)

                                    val scale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.15f else 1f,
                                        label = "serverScale"
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(72.dp)
                                            .graphicsLayer(scaleX = scale, scaleY = scale)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .background(bgColor)
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(2.dp, Color.White, CircleShape)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable {
                                                    currentServerIndex = index
                                                    isLoading = true
                                                    hasError = false
                                                    showServerDropdown = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OndemandVideo,
                                                contentDescription = serverPair.first,
                                                tint = Color.White,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            if (isSelected) {
                                                // Semi-transparent overlay to emphasize checkmark
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Server Label
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (isMainServer) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Main Server",
                                                    tint = Color(0xFFFFD700),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                            }
                                            Text(
                                                text = serverPair.first,
                                                fontSize = if (isMainServer) 11.sp else 9.sp,
                                                fontWeight = if (isMainServer) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) NeonCyan else (if (isDark) Color.LightGray else Color.DarkGray),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Pad trailing empty slots to ensure consistent 4-column alignment
                                repeat(4 - rowItems.size) {
                                    Spacer(modifier = Modifier.width(72.dp))
                                }
                            }
                        }
                    }

                    // Progressive Disclosure Button
                    if (embedServers.size > 15) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) "Show Less" else "See More (${embedServers.size - 15} More)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Clean YouTube trailer dialog
        if (showTrailerDialog) {
            YouTubeTrailerModal(
                showModal = showTrailerDialog,
                onDismiss = { showTrailerDialog = false },
                title = title,
                trailerId = youtubeTrailerId,
                isLoading = youtubeTrailerId.isNullOrBlank()
            )
        }

        // Show Content Info Bottom Plate when clicking any Related Media Card
        selectedDetailItem?.let { item ->
            val isFav by viewModel.isMediaFavoriteStream(item.id).collectAsState(initial = false)
            com.example.ui.screens.MediaDetailSheet(
                item = item,
                isFavorite = isFav,
                onFavoriteToggle = { viewModel.toggleMediaFavorite(item, isFav) },
                onDismiss = { selectedDetailItem = null },
                onPlayStream = { s, ep ->
                    selectedDetailItem = null
                    onSelectMedia(item)
                },
                viewModel = viewModel
            )
        }

        // Subscription Plan Selection Modal
        SubscriptionPlanModal(
            isVisible = showSubscriptionPlanModal,
            onDismiss = { showSubscriptionPlanModal = false }
        )
  }
}

@Composable
fun RelatedMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnime = (item.category.contains("anime", ignoreCase = true) || item.type.equals("anime", ignoreCase = true)) && !item.category.equals("Anime & Series", ignoreCase = true)
    var posterToLoad by remember(item.imageUrl) { mutableStateOf(item.imageUrl) }
    var isLoadFailed by remember(posterToLoad) { mutableStateOf(false) }

    LaunchedEffect(item.title, isAnime) {
        if (posterToLoad.isBlank() && isAnime) {
            val enriched = withContext(Dispatchers.IO) {
                com.example.scraper.AnimePosterEngine.getAnimePosterAndBanner(item.title)
            }
            if (enriched != null && enriched.posterUrl.isNotBlank()) {
                posterToLoad = enriched.posterUrl
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier
            .clickable { onClick() }
            .testTag("related_media_card_${item.id}")
    ) {
        Column {
            // 1. Poster on Top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E1B4B), SpaceBlack)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (posterToLoad.isNotBlank() && !isLoadFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(posterToLoad)
                            .crossfade(true)
                            .listener(
                                onError = { _, _ ->
                                    isLoadFailed = true
                                }
                            )
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback visual tile
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isAnime) Icons.Default.AutoAwesome else Icons.Default.Movie,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.title.take(15),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Premium VIP Badge Overlay Top Left
                if (item.isPremium) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopStart)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "VIP",
                                tint = Color.Black,
                                modifier = Modifier.size(9.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "VIP",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // IMDb Rating Overlay Top Right
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = item.rating,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Play Button Center
                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                        .background(NeonCyan.copy(alpha = 0.9f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Name / Title below poster
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 3. Rating & Category below name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = item.rating,
                            color = Color(0xFFFFD700),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = item.category,
                        color = NeonCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
