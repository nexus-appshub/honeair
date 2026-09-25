package com.example.data.repository

import com.example.data.database.AdminLogEntity
import com.example.data.database.AdminLogDao
import com.example.data.database.FavoriteDao
import com.example.data.database.FavoriteEntity
import com.example.data.database.HistoryDao
import com.example.data.database.HistoryEntity
import com.example.data.model.IptvChannel
import com.example.data.model.IptvPlaylist
import android.content.Context
import com.example.data.network.IptvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StreamRepository(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val adminLogDao: AdminLogDao,
    private val mediaHistoryDao: com.example.data.database.MediaHistoryDao,
    private val mediaFavoriteDao: com.example.data.database.MediaFavoriteDao,
    private val context: Context
) {
    // Local In-Memory Cache
    private var playlistCache: List<IptvPlaylist> = emptyList()
    private val channelCache = mutableMapOf<String, List<IptvChannel>>()
    private var updateChannelsCache: List<IptvChannel>? = null

    // Database Flows
    val favorites: Flow<List<IptvChannel>> = favoriteDao.getAllFavorites().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val watchHistory: Flow<List<IptvChannel>> = historyDao.getWatchHistory().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val mediaFavorites: Flow<List<com.example.data.model.MediaItem>> = mediaFavoriteDao.getAllMediaFavorites().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val mediaWatchHistory: Flow<List<com.example.data.model.MediaItem>> = mediaHistoryDao.getMediaHistory().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val adminLogs: Flow<List<AdminLogEntity>> = adminLogDao.getAllLogs()

    // Network Operations
    suspend fun getIndexPlaylists(forceRefresh: Boolean = false): List<IptvPlaylist> = withContext(Dispatchers.IO) {
        if (!forceRefresh && playlistCache.isNotEmpty()) {
            return@withContext playlistCache
        }
        val curatedList = listOf(
            IptvPlaylist(
                name = "LIVE TV UPDATED 📺",
                url = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8",
                group = "Recommended",
                logo = "https://img.icons8.com/fluency/96/tv.png"
            ),
            IptvPlaylist(
                name = "UPDATE CHANNELS 📺",
                url = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8",
                group = "Recommended",
                logo = "https://img.icons8.com/color/96/tv-show.png"
            ),
            IptvPlaylist(
                name = "Fixed Channels 📺",
                url = "asset://fixed_channels.enc",
                group = "Recommended",
                logo = "https://img.icons8.com/fluency/96/tv.png"
            ),
            IptvPlaylist(
                name = "Sport TV 📺",
                url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
                group = "Recommended",
                logo = "https://img.icons8.com/color/96/trophy.png"
            ),
            IptvPlaylist(
                name = "Bangladesh 🇧🇩",
                url = "https://iptv-org.github.io/iptv/countries/bd.m3u",
                group = "Countries",
                logo = "https://flagcdn.com/w320/bd.png"
            ),
            IptvPlaylist(
                name = "India 🇮🇳",
                url = "https://iptv-org.github.io/iptv/countries/in.m3u",
                group = "Countries",
                logo = "https://flagcdn.com/w320/in.png"
            ),
            IptvPlaylist(
                name = "United States 🇺🇸",
                url = "https://iptv-org.github.io/iptv/countries/us.m3u",
                group = "Countries",
                logo = "https://flagcdn.com/w320/us.png"
            ),
            IptvPlaylist(
                name = "United Kingdom 🇬🇧",
                url = "https://iptv-org.github.io/iptv/countries/uk.m3u",
                group = "Countries",
                logo = "https://flagcdn.com/w320/gb.png"
            ),
            IptvPlaylist(
                name = "Canada 🇨🇦",
                url = "https://iptv-org.github.io/iptv/countries/ca.m3u",
                group = "Countries",
                logo = "https://flagcdn.com/w320/ca.png"
            ),
            IptvPlaylist(
                name = "Saudi Arabia 🇸🇦",
                url = "https://iptv-org.github.io/iptv/countries/sa.m3u",
                group = "Countries",
                logo = "https://flagcdn.com/w320/sa.png"
            ),
            IptvPlaylist(
                name = "United Arab Emirates 🇦🇪",
                url = "https://iptv-org.github.io/iptv/countries/ae.m3u",
                group = "Countries",
                logo = "https://flagcdn.com/w320/ae.png"
            ),
            IptvPlaylist(
                name = "International News 📰",
                url = "https://iptv-org.github.io/iptv/categories/news.m3u",
                group = "Categories",
                logo = "https://img.icons8.com/fluency/96/news.png"
            ),
            IptvPlaylist(
                name = "Sports Channels ⚽",
                url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
                group = "Categories",
                logo = "https://img.icons8.com/fluency/96/sports.png"
            ),
            IptvPlaylist(
                name = "Movies & Cinema 🎬",
                url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
                group = "Categories",
                logo = "https://img.icons8.com/fluency/96/movie.png"
            ),
            IptvPlaylist(
                name = "Music Channels 🎵",
                url = "https://iptv-org.github.io/iptv/categories/music.m3u",
                group = "Categories",
                logo = "https://img.icons8.com/fluency/96/musical-notes.png"
            ),
            IptvPlaylist(
                name = "Documentary & Nature 🌍",
                url = "https://iptv-org.github.io/iptv/categories/documentary.m3u",
                group = "Categories",
                logo = "https://img.icons8.com/fluency/96/documentary.png"
            ),
            IptvPlaylist(
                name = "Kids & Cartoon 🧸",
                url = "https://iptv-org.github.io/iptv/categories/kids.m3u",
                group = "Categories",
                logo = "https://img.icons8.com/fluency/96/children.png"
            )
        )
        playlistCache = curatedList
        addLog("SYSTEM", "Index Load Success", "Parsed ${curatedList.size} curated playlists successfully.")
        curatedList
    }

    suspend fun getChannelsFromPlaylist(playlistUrl: String, forceRefresh: Boolean = false): List<IptvChannel> = withContext(Dispatchers.IO) {
        if (playlistUrl == "demo://channels") {
            return@withContext listOf(
                IptvChannel(
                    name = "France 24 English Live News",
                    url = "https://static.france24.com/live/F24_EN_LO_HLS/live_web.m3u8",
                    logo = "https://img.icons8.com/color/96/france-news.png",
                    group = "Live News",
                    tvgId = "demo_f24_hls"
                ),
                IptvChannel(
                    name = "Red Bull Sports Live",
                    url = "https://rbmn-live.akamaized.net/hls/live/590964/bo1/master.m3u8",
                    logo = "https://img.icons8.com/color/96/red-bull.png",
                    group = "Sports Live",
                    tvgId = "demo_redbull_hls"
                ),
                IptvChannel(
                    name = "DW News World Feed",
                    url = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
                    logo = "https://img.icons8.com/color/96/news.png",
                    group = "Live News",
                    tvgId = "demo_dw_hls"
                ),
                IptvChannel(
                    name = "Al Jazeera English News",
                    url = "https://live-hls-web-aje.getaj.net/AJE/index.m3u8",
                    logo = "https://img.icons8.com/color/96/tv.png",
                    group = "Live News",
                    tvgId = "demo_aje_hls"
                )
            )
        }
        if (!forceRefresh && channelCache.containsKey(playlistUrl)) {
            return@withContext channelCache[playlistUrl] ?: emptyList()
        }
        if (playlistUrl.startsWith("asset://")) {
            try {
                var assetName = playlistUrl.removePrefix("asset://")
                if (assetName == "fixed_channels.m3u") {
                    assetName = "fixed_channels.enc"
                }
                addLog("SYSTEM", "Loading Encrypted Channels", "Decrypting secure channels from asset: $assetName")
                val cipherBytes = context.assets.open(assetName).readBytes()
                val keyBytes = "HomeAirTvSecurityKey2026SecretM3uKey".toByteArray(Charsets.UTF_8)
                val decryptedBytes = ByteArray(cipherBytes.size)
                for (i in cipherBytes.indices) {
                    decryptedBytes[i] = (cipherBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
                }
                val raw = String(decryptedBytes, Charsets.UTF_8)
                val parsed = IptvParser.parseChannels(raw)
                channelCache[playlistUrl] = parsed
                addLog("SYSTEM", "Channels Load Success", "Parsed ${parsed.size} stream channels from encrypted asset $assetName successfully.")
                return@withContext parsed
            } catch (e: Exception) {
                addLog("ERROR", "Channels Asset Load Failed", "Error loading asset channels: ${e.localizedMessage}")
                throw e
            }
        }
        try {
            addLog("SYSTEM", "Fetching Channels", "Requesting channels from sub-playlist: $playlistUrl")
            val raw = IptvParser.fetchRawContent(playlistUrl)
            val parsed = IptvParser.parseChannels(raw)

            val isBdPlaylist = playlistUrl.contains("bd.m3u", ignoreCase = true)
            val isInPlaylist = playlistUrl.contains("in.m3u", ignoreCase = true)

            val finalResult = if (isBdPlaylist || isInPlaylist) {
                try {
                    val updateChannels = updateChannelsCache ?: run {
                        val updateUrl = "https://raw.githubusercontent.com/nexus-appshub/homeairtv.xyz/main/hmairtv.m3u8"
                        val updateRaw = IptvParser.fetchRawContent(updateUrl)
                        val parsedUpdate = IptvParser.parseChannels(updateRaw)
                        updateChannelsCache = parsedUpdate
                        parsedUpdate
                    }
                    
                    val keywords = if (isBdPlaylist) {
                        listOf("bangla", "bangladesh", "bd", "btv", "somoy", "ekattor", "jamuna", "independent", "ntv", "atn", "rtv", "gtv", "t sports", "dbc", "channel 24", "news24", "deepto", "nagorik", "boishakhi", "maasranga", "my tv", "asian tv", "bijoy", "duronto", "sa tv", "mohona", "nexus", "global tv", "gazi", "channel i")
                    } else {
                        listOf("india", "hindi", "star", "zee", "colors", "sony", "aaj tak", "ndtv", "abp", "sun", "vijay", "tamil", "telugu", "malayalam", "kannada", "marathi", "punjabi", "sports 18", "jio", "asianet", "sab", "goldmines", "b4u", "dangal")
                    }
                    
                    val filteredFromUpdate = updateChannels.filter { ch ->
                        val lowerName = ch.name.lowercase()
                        val lowerGroup = ch.group.lowercase()
                        keywords.any { kw -> lowerName.contains(kw) || lowerGroup.contains(kw) }
                    }
                    
                    (filteredFromUpdate + parsed).distinctBy { if (it.url.isNotBlank()) it.url else it.name }
                } catch (e: Exception) {
                    parsed
                }
            } else {
                parsed
            }

            channelCache[playlistUrl] = finalResult
            addLog("SYSTEM", "Channels Load Success", "Parsed ${finalResult.size} stream channels successfully.")
            finalResult
        } catch (e: Exception) {
            addLog("ERROR", "Channels Fetch Failed", "Error loading channels: ${e.localizedMessage}")
            throw e
        }
    }

    // Favorites Management
    suspend fun addFavorite(channel: IptvChannel) = withContext(Dispatchers.IO) {
        favoriteDao.insertFavorite(channel.toEntity())
        addLog("INFO", "Added Favorite", "Channel '${channel.name}' added to favorites.")
    }

    suspend fun removeFavorite(channel: IptvChannel) = withContext(Dispatchers.IO) {
        favoriteDao.deleteFavorite(channel.toEntity())
        addLog("INFO", "Removed Favorite", "Channel '${channel.name}' removed from favorites.")
    }

    fun isFavorite(url: String): Flow<Boolean> {
        return favoriteDao.isFavorite(url)
    }

    // Watch History Management
    suspend fun addToHistory(channel: IptvChannel) = withContext(Dispatchers.IO) {
        historyDao.insertHistory(
            HistoryEntity(
                url = channel.url,
                name = channel.name,
                logo = channel.logo,
                groupName = channel.group,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao.clearHistory()
        addLog("INFO", "Cleared History", "User watch history has been wiped.")
    }

    // Admin Logger
    suspend fun addLog(type: String, title: String, message: String) {
        try {
            adminLogDao.insertLog(
                AdminLogEntity(
                    type = type,
                    title = title,
                    message = message
                )
            )
        } catch (e: Exception) {
            // No-op to avoid crashing during DB logger error
        }
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        adminLogDao.clearAllLogs()
    }

    // Converters
    private fun FavoriteEntity.toDomainModel(): IptvChannel = IptvChannel(
        name = name,
        url = url,
        logo = logo,
        group = groupName,
        tvgId = tvgId
    )

    private fun HistoryEntity.toDomainModel(): IptvChannel = IptvChannel(
        name = name,
        url = url,
        logo = logo,
        group = groupName,
        tvgId = ""
    )

    // MediaItem Converters
    private fun com.example.data.database.MediaFavoriteEntity.toDomainModel(): com.example.data.model.MediaItem = com.example.data.model.MediaItem(
        id = id, title = title, category = category, imageUrl = imageUrl,
        rating = rating, year = year, description = description, streamUrl = streamUrl,
        episodes = episodes, isStreamable = isStreamable, imdbId = imdbId, type = type
    )

    private fun com.example.data.database.MediaHistoryEntity.toDomainModel(): com.example.data.model.MediaItem = com.example.data.model.MediaItem(
        id = id, title = title, category = category, imageUrl = imageUrl,
        rating = rating, year = year, description = description, streamUrl = streamUrl,
        episodes = episodes, isStreamable = isStreamable, imdbId = imdbId, type = type
    )

    private fun com.example.data.model.MediaItem.toFavoriteEntity(): com.example.data.database.MediaFavoriteEntity = com.example.data.database.MediaFavoriteEntity(
        id = id, title = title, category = category, imageUrl = imageUrl,
        rating = rating, year = year, description = description, streamUrl = streamUrl,
        episodes = episodes, isStreamable = isStreamable, imdbId = imdbId, type = type
    )

    private fun com.example.data.model.MediaItem.toHistoryEntity(): com.example.data.database.MediaHistoryEntity = com.example.data.database.MediaHistoryEntity(
        id = id, title = title, category = category, imageUrl = imageUrl,
        rating = rating, year = year, description = description, streamUrl = streamUrl,
        episodes = episodes, isStreamable = isStreamable, imdbId = imdbId, type = type,
        timestamp = System.currentTimeMillis()
    )

    // Media Favorites Management
    suspend fun addMediaFavorite(item: com.example.data.model.MediaItem) = withContext(Dispatchers.IO) {
        mediaFavoriteDao.insertMediaFavorite(item.toFavoriteEntity())
    }

    suspend fun removeMediaFavorite(item: com.example.data.model.MediaItem) = withContext(Dispatchers.IO) {
        mediaFavoriteDao.deleteMediaFavorite(item.toFavoriteEntity())
    }

    fun isMediaFavorite(id: String): Flow<Boolean> {
        return mediaFavoriteDao.isMediaFavorite(id)
    }

    // Media Watch History Management
    suspend fun addMediaToHistory(item: com.example.data.model.MediaItem) = withContext(Dispatchers.IO) {
        mediaHistoryDao.insertMediaHistory(item.toHistoryEntity())
    }

    suspend fun clearMediaHistory() = withContext(Dispatchers.IO) {
        mediaHistoryDao.clearMediaHistory()
    }

    private fun IptvChannel.toEntity(): FavoriteEntity = FavoriteEntity(
        url = url,
        name = name,
        logo = logo,
        groupName = group,
        tvgId = tvgId
    )
}
