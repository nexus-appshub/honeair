package com.example.data.repository

import com.example.data.model.AiringSource
import com.example.data.model.ShortReel
import com.example.data.network.ShortReelsApiClient
import com.example.data.network.ShortReelsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class ShortReelsResult {
    data class Success(
        val sessionId: String?,
        val reels: List<ShortReel>,
        val hasMore: Boolean
    ) : ShortReelsResult()

    data class Error(val message: String, val isSessionExpired: Boolean = false) : ShortReelsResult()
}

class ShortReelsRepository(
    private val apiService: ShortReelsApiService = ShortReelsApiClient.apiService
) {

    suspend fun fetchInitialFeed(source: AiringSource, limit: Int = 10): ShortReelsResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getFeedByUrl(url = source.internalUrl, limit = limit)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val rawItems = (body.newItems ?: emptyList()) + (body.items ?: emptyList())
                        val validReels = rawItems.mapNotNull { it.toValidShortReel() }
                            .distinctBy { it.mediaUrl }
                            .distinctBy { it.id }

                        val hasMore = body.hasMore ?: (validReels.isNotEmpty())
                        ShortReelsResult.Success(
                            sessionId = body.sessionId,
                            reels = validReels,
                            hasMore = hasMore
                        )
                    } else {
                        ShortReelsResult.Error("Server returned empty response.")
                    }
                } else {
                    val code = response.code()
                    mapHttpError(code)
                }
            } catch (e: Exception) {
                mapExceptionError(e)
            }
        }
    }

    suspend fun fetchNextPage(sessionId: String, limit: Int = 10): ShortReelsResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getFeedBySessionId(sessionId = sessionId, limit = limit)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val rawItems = (body.newItems ?: emptyList()).ifEmpty { body.items ?: emptyList() }
                        val validReels = rawItems.mapNotNull { it.toValidShortReel() }
                            .distinctBy { it.mediaUrl }
                            .distinctBy { it.id }

                        val hasMore = body.hasMore ?: (validReels.isNotEmpty())
                        ShortReelsResult.Success(
                            sessionId = body.sessionId ?: sessionId,
                            reels = validReels,
                            hasMore = hasMore
                        )
                    } else {
                        ShortReelsResult.Success(
                            sessionId = sessionId,
                            reels = emptyList(),
                            hasMore = false
                        )
                    }
                } else {
                    val code = response.code()
                    if (code == 404 || code == 410) {
                        ShortReelsResult.Error("Session expired. Refreshing feed...", isSessionExpired = true)
                    } else {
                        mapHttpError(code)
                    }
                }
            } catch (e: Exception) {
                mapExceptionError(e)
            }
        }
    }

    private fun mapHttpError(code: Int): ShortReelsResult.Error {
        val userFriendlyMessage = when (code) {
            429 -> "Server is busy. Please try again."
            502, 503, 504 -> "Server is temporarily unavailable."
            400, 404 -> "Unable to load feed. Please try again."
            else -> "Server is temporarily unavailable."
        }
        return ShortReelsResult.Error(userFriendlyMessage)
    }

    private fun mapExceptionError(e: Exception): ShortReelsResult.Error {
        val userFriendlyMessage = when (e) {
            is SocketTimeoutException -> "Server is taking longer than usual."
            is UnknownHostException -> "No internet connection. Please check network."
            else -> "Server is temporarily unavailable."
        }
        return ShortReelsResult.Error(userFriendlyMessage)
    }
}
