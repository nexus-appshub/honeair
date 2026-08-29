package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.MediaComment
import com.example.data.model.MediaRatingSummary
import com.example.ui.viewmodel.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MediaInteractionRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val database = AppDatabase.getDatabase(context)
    private val commentDao = database.mediaCommentDao()

    companion object {
        private const val TAG = "MediaInteractionRepo"

        // --- Supabase PostgreSQL Database Settings ---
        // Replace with your Supabase Project URL and Anon Key
        var SUPABASE_URL = "" // e.g., "https://xyzcompany.supabase.co"
        var SUPABASE_ANON_KEY = "" // e.g., "eyJhbGciOiJIUzI1NiIsInR5cCI6..."

        // --- Firebase Fallback Settings ---
        private const val FIREBASE_API_KEY = "AIzaSyBCasqe4hKjCauoRYwbg1GPAwNJRLWGw5w"
        private const val RTDB_REST_BASE = "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    private var currentActiveImdbId: String? = null
    private var currentActiveUserEmail: String? = null

    private val _commentsState = MutableStateFlow<List<MediaComment>>(emptyList())
    val commentsState: StateFlow<List<MediaComment>> = _commentsState.asStateFlow()

    private val _ratingSummaryState = MutableStateFlow(MediaRatingSummary())
    val ratingSummaryState: StateFlow<MediaRatingSummary> = _ratingSummaryState.asStateFlow()

    fun attachMedia(imdbId: String, currentUserEmail: String?) {
        if (imdbId.isBlank()) return
        if (currentActiveImdbId == imdbId && currentActiveUserEmail == currentUserEmail) return

        currentActiveImdbId = imdbId
        currentActiveUserEmail = currentUserEmail

        // 1. Instantly load offline Room cached comments
        coroutineScope.launch(Dispatchers.IO) {
            commentDao.getCommentsForMedia(imdbId).collect { entities ->
                val cached = entities.map { MediaComment.fromEntity(it) }
                if (_commentsState.value.isEmpty() && cached.isNotEmpty()) {
                    _commentsState.value = cached
                }
            }
        }

        // 2. Cloud Database Sync Engine (Supabase SQL or Firebase RTDB)
        coroutineScope.launch(Dispatchers.IO) {
            if (isSupabaseConfigured()) {
                syncCommentsFromSupabase(imdbId)
                syncRatingsFromSupabase(imdbId, currentUserEmail)
            } else {
                syncCommentsFromFirebase(imdbId)
                syncRatingsFromFirebase(imdbId, currentUserEmail)
            }
        }
    }

    private fun isSupabaseConfigured(): Boolean {
        return SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()
    }

    private fun mergeAndEmitComments(incomingComments: List<MediaComment>) {
        if (incomingComments.isEmpty()) return
        val current = _commentsState.value
        val map = HashMap<String, MediaComment>()
        incomingComments.forEach { map[it.id] = it }
        current.forEach { 
            if (!map.containsKey(it.id)) {
                map[it.id] = it 
            }
        }
        val merged = map.values.sortedByDescending { it.timestamp }
        _commentsState.value = merged

        // Save to Room DB cache
        coroutineScope.launch(Dispatchers.IO) {
            commentDao.insertComments(merged.map { it.toEntity() })
        }
    }

    // ==========================================
    // SUPABASE SQL DATABASE SYNC ENGINE
    // ==========================================

    private fun syncCommentsFromSupabase(imdbId: String) {
        try {
            val url = "$SUPABASE_URL/rest/v1/media_comments?imdb_id=eq.$imdbId&select=*&order=timestamp.desc"
            val headers = mapOf(
                "apikey" to SUPABASE_ANON_KEY,
                "Authorization" to "Bearer $SUPABASE_ANON_KEY"
            )
            val response = executeHttpRequest(url, "GET", headers = headers) ?: return
            val jsonArray = JSONArray(response)
            val comments = mutableListOf<MediaComment>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val docImdbId = obj.optString("imdb_id", imdbId)
                val userId = obj.optString("user_id", "")
                val userEmail = obj.optString("user_email", "")
                val userName = obj.optString("user_name", "User")
                val userAvatar = obj.optString("user_avatar", "")
                val text = obj.optString("text", "")
                val rating = obj.optDouble("rating", 0.0).toFloat()
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val likesCount = obj.optInt("likes_count", 0)
                val parentId = obj.optString("parent_id", "")
                val replyToUserName = obj.optString("reply_to_user_name", "")

                val likedByArr = obj.optJSONArray("liked_by")
                val likedBy = mutableListOf<String>()
                if (likedByArr != null) {
                    for (j in 0 until likedByArr.length()) {
                        val likedUser = likedByArr.optString(j)
                        if (likedUser.isNotBlank()) likedBy.add(likedUser)
                    }
                }

                if (text.isNotBlank()) {
                    comments.add(
                        MediaComment(
                            id = id,
                            imdbId = docImdbId,
                            userId = userId,
                            userEmail = userEmail,
                            userName = userName,
                            userAvatar = userAvatar,
                            text = text,
                            rating = rating,
                            timestamp = timestamp,
                            likesCount = likesCount,
                            parentId = parentId,
                            replyToUserName = replyToUserName,
                            likedBy = likedBy
                        )
                    )
                }
            }

            if (comments.isNotEmpty()) {
                mergeAndEmitComments(comments)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncCommentsFromSupabase", e)
        }
    }

    private fun syncRatingsFromSupabase(imdbId: String, currentUserEmail: String?) {
        try {
            val url = "$SUPABASE_URL/rest/v1/media_ratings?imdb_id=eq.$imdbId&select=*"
            val headers = mapOf(
                "apikey" to SUPABASE_ANON_KEY,
                "Authorization" to "Bearer $SUPABASE_ANON_KEY"
            )
            val response = executeHttpRequest(url, "GET", headers = headers) ?: return
            val jsonArray = JSONArray(response)

            var sum = 0.0
            var count = 0
            var myRating = 0f
            val safeEmail = currentUserEmail?.trim()?.lowercase() ?: ""

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val r = obj.optDouble("rating", 0.0)
                if (r > 0) {
                    sum += r
                    count++
                    val docEmail = obj.optString("user_email", "").trim().lowercase()
                    if (safeEmail.isNotEmpty() && docEmail == safeEmail) {
                        myRating = r.toFloat()
                    }
                }
            }

            if (count > 0) {
                val avg = (sum / count).toFloat()
                _ratingSummaryState.value = MediaRatingSummary(
                    imdbId = imdbId,
                    averageRating = String.format("%.1f", avg).toFloatOrNull() ?: avg,
                    totalRatings = count,
                    userRating = if (myRating > 0f) myRating else _ratingSummaryState.value.userRating
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncRatingsFromSupabase", e)
        }
    }

    // ==========================================
    // FIREBASE REST API FALLBACK ENGINE
    // ==========================================

    private fun syncCommentsFromFirebase(imdbId: String) {
        try {
            val url = "$RTDB_REST_BASE/mediaComments.json?key=$FIREBASE_API_KEY"
            val response = executeHttpRequest(url, "GET") ?: return
            val json = JSONObject(response)
            val comments = mutableListOf<MediaComment>()
            val keys = json.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.optJSONObject(key) ?: continue
                val docImdbId = obj.optString("imdbId", "")
                if (docImdbId.equals(imdbId, ignoreCase = true)) {
                    val id = obj.optString("id", key)
                    val userEmail = obj.optString("userEmail", "")
                    val userName = obj.optString("userName", "User")
                    val userAvatar = obj.optString("userAvatar", "")
                    val text = obj.optString("text", "")
                    val rating = obj.optDouble("rating", 0.0).toFloat()
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    
                    val likedByArr = obj.optJSONArray("likedBy")
                    val likedBy = mutableListOf<String>()
                    if (likedByArr != null) {
                        for (j in 0 until likedByArr.length()) {
                            val likedUser = likedByArr.optString(j)
                            if (likedUser.isNotBlank()) likedBy.add(likedUser)
                        }
                    }
                    val likesCount = obj.optInt("likesCount", likedBy.size)
                    val parentId = obj.optString("parentId", "")
                    val replyToUserName = obj.optString("replyToUserName", "")
                    val userId = obj.optString("userId", "")

                    if (text.isNotBlank()) {
                        comments.add(
                            MediaComment(
                                id = id,
                                imdbId = docImdbId,
                                userId = userId,
                                userEmail = userEmail,
                                userName = userName,
                                userAvatar = userAvatar,
                                text = text,
                                rating = rating,
                                timestamp = timestamp,
                                likesCount = likesCount,
                                parentId = parentId,
                                replyToUserName = replyToUserName,
                                likedBy = likedBy
                            )
                        )
                    }
                }
            }

            if (comments.isNotEmpty()) {
                mergeAndEmitComments(comments)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncCommentsFromFirebase", e)
        }
    }

    private fun syncRatingsFromFirebase(imdbId: String, currentUserEmail: String?) {
        try {
            val url = "$RTDB_REST_BASE/mediaRatings.json?key=$FIREBASE_API_KEY"
            val response = executeHttpRequest(url, "GET") ?: return
            val json = JSONObject(response)
            val keys = json.keys()

            var sum = 0.0
            var count = 0
            var myRating = 0f
            val safeEmail = currentUserEmail?.trim()?.lowercase() ?: ""

            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.optJSONObject(key) ?: continue
                val docImdbId = obj.optString("imdbId", "")
                if (docImdbId.equals(imdbId, ignoreCase = true)) {
                    val r = obj.optDouble("rating", 0.0)
                    if (r > 0) {
                        sum += r
                        count++
                        val docEmail = obj.optString("userEmail", "").trim().lowercase()
                        if (safeEmail.isNotEmpty() && docEmail == safeEmail) {
                            myRating = r.toFloat()
                        }
                    }
                }
            }

            if (count > 0) {
                val avg = (sum / count).toFloat()
                _ratingSummaryState.value = MediaRatingSummary(
                    imdbId = imdbId,
                    averageRating = String.format("%.1f", avg).toFloatOrNull() ?: avg,
                    totalRatings = count,
                    userRating = if (myRating > 0f) myRating else _ratingSummaryState.value.userRating
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncRatingsFromFirebase", e)
        }
    }

    // ==========================================
    // POSTING COMMENTS & RATINGS
    // ==========================================

    fun postComment(
        imdbId: String,
        user: UserProfile,
        text: String,
        rating: Float = 0f,
        parentId: String = "",
        replyToUserName: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            onError("Comment cannot be empty.")
            return
        }

        val commentId = UUID.randomUUID().toString()
        val finalUid = if (user.userId.isNotBlank()) user.userId else "user_${System.currentTimeMillis()}"

        val newComment = MediaComment(
            id = commentId,
            imdbId = imdbId,
            userId = finalUid,
            userEmail = user.email,
            userName = user.name,
            userAvatar = user.avatarUrl,
            text = trimmedText,
            rating = rating,
            timestamp = System.currentTimeMillis(),
            likesCount = 0,
            parentId = parentId,
            replyToUserName = replyToUserName,
            likedBy = emptyList()
        )

        // 1. Instant local optimistic update & Room Cache
        _commentsState.value = listOf(newComment) + _commentsState.value.filter { it.id != commentId }
        coroutineScope.launch(Dispatchers.IO) {
            commentDao.insertComment(newComment.toEntity())
        }

        // 2. Cloud Database Sync (Supabase or Firebase)
        coroutineScope.launch(Dispatchers.IO) {
            var cloudSuccess = false
            var errorMsg: String? = null

            if (isSupabaseConfigured()) {
                try {
                    val url = "$SUPABASE_URL/rest/v1/media_comments"
                    val body = JSONObject().apply {
                        put("id", commentId)
                        put("imdb_id", imdbId)
                        put("user_id", finalUid)
                        put("user_email", user.email)
                        put("user_name", user.name)
                        put("user_avatar", user.avatarUrl)
                        put("text", trimmedText)
                        put("rating", rating.toDouble())
                        put("timestamp", newComment.timestamp)
                        put("likes_count", 0)
                        put("parent_id", parentId)
                        put("reply_to_user_name", replyToUserName)
                        put("liked_by", JSONArray())
                    }.toString()

                    val headers = mapOf(
                        "apikey" to SUPABASE_ANON_KEY,
                        "Authorization" to "Bearer $SUPABASE_ANON_KEY",
                        "Prefer" to "resolution=merge-duplicates"
                    )

                    val response = executeHttpRequest(url, "POST", body, headers)
                    if (response != null) {
                        cloudSuccess = true
                    }
                } catch (e: Exception) {
                    errorMsg = e.message
                    Log.e(TAG, "Supabase post comment failed", e)
                }
            } else {
                try {
                    val url = "$RTDB_REST_BASE/mediaComments/$commentId.json?key=$FIREBASE_API_KEY"
                    val body = JSONObject().apply {
                        put("id", commentId)
                        put("imdbId", imdbId)
                        put("userId", finalUid)
                        put("userEmail", user.email)
                        put("userName", user.name)
                        put("userAvatar", user.avatarUrl)
                        put("text", trimmedText)
                        put("rating", rating.toDouble())
                        put("timestamp", newComment.timestamp)
                        put("likesCount", 0)
                        put("parentId", parentId)
                        put("replyToUserName", replyToUserName)
                        put("likedBy", JSONArray())
                    }.toString()

                    val response = executeHttpRequest(url, "PUT", body)
                    if (response != null) {
                        cloudSuccess = true
                    }
                } catch (e: Exception) {
                    errorMsg = e.message
                    Log.e(TAG, "Firebase post comment failed", e)
                }
            }

            coroutineScope.launch(Dispatchers.Main) {
                if (cloudSuccess) {
                    onSuccess()
                } else {
                    Log.w(TAG, "Cloud sync warning: ${errorMsg ?: "Network error"}")
                    onSuccess()
                }
            }
        }
    }

    fun toggleLikeComment(
        commentId: String,
        userEmail: String,
        onSuccess: () -> Unit = {}
    ) {
        if (userEmail.isBlank()) return

        val currentList = _commentsState.value
        val comment = currentList.find { it.id == commentId } ?: return
        val userKey = userEmail.trim().lowercase()

        val alreadyLiked = comment.likedBy.any { it.trim().equals(userKey, ignoreCase = true) }
        val newLikedBy = if (alreadyLiked) {
            comment.likedBy.filterNot { it.trim().equals(userKey, ignoreCase = true) }
        } else {
            comment.likedBy + userKey
        }
        val newLikesCount = newLikedBy.size

        val updatedComment = comment.copy(
            likedBy = newLikedBy,
            likesCount = newLikesCount
        )

        _commentsState.value = currentList.map { if (it.id == commentId) updatedComment else it }
        coroutineScope.launch(Dispatchers.IO) {
            commentDao.insertComment(updatedComment.toEntity())
        }

        coroutineScope.launch(Dispatchers.IO) {
            if (isSupabaseConfigured()) {
                try {
                    val url = "$SUPABASE_URL/rest/v1/media_comments?id=eq.$commentId"
                    val body = JSONObject().apply {
                        put("likes_count", newLikesCount)
                        val likedByArr = JSONArray()
                        newLikedBy.forEach { likedByArr.put(it) }
                        put("liked_by", likedByArr)
                    }.toString()
                    val headers = mapOf(
                        "apikey" to SUPABASE_ANON_KEY,
                        "Authorization" to "Bearer $SUPABASE_ANON_KEY"
                    )
                    executeHttpRequest(url, "PATCH", body, headers)
                } catch (e: Throwable) {
                    Log.e(TAG, "Supabase update like error", e)
                }
            } else {
                try {
                    val url = "$RTDB_REST_BASE/mediaComments/$commentId.json?key=$FIREBASE_API_KEY"
                    val body = JSONObject().apply {
                        put("likesCount", newLikesCount)
                        val likedByArr = JSONArray()
                        newLikedBy.forEach { likedByArr.put(it) }
                        put("likedBy", likedByArr)
                    }.toString()
                    executeHttpRequest(url, "PATCH", body)
                } catch (e: Throwable) {
                    Log.e(TAG, "Firebase update like error", e)
                }
            }
        }

        onSuccess()
    }

    fun submitRating(
        imdbId: String,
        user: UserProfile,
        rating: Float,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (rating <= 0f) return

        val cleanEmailKey = user.email.trim().lowercase().replace(".", "_").replace("@", "_at_")
        val docId = "${imdbId}_$cleanEmailKey"

        val curSummary = _ratingSummaryState.value
        val newTotal = if (curSummary.userRating > 0f) curSummary.totalRatings else curSummary.totalRatings + 1
        val newAvg = if (curSummary.totalRatings > 0) {
            ((curSummary.averageRating * curSummary.totalRatings + rating) / (curSummary.totalRatings + 1))
        } else {
            rating
        }

        _ratingSummaryState.value = curSummary.copy(
            imdbId = imdbId,
            averageRating = String.format("%.1f", newAvg).toFloatOrNull() ?: newAvg,
            totalRatings = maxOf(1, newTotal),
            userRating = rating
        )

        val finalUid = if (user.userId.isNotBlank()) user.userId else "user_${System.currentTimeMillis()}"

        coroutineScope.launch(Dispatchers.IO) {
            if (isSupabaseConfigured()) {
                try {
                    val url = "$SUPABASE_URL/rest/v1/media_ratings"
                    val body = JSONObject().apply {
                        put("doc_id", docId)
                        put("imdb_id", imdbId)
                        put("user_id", finalUid)
                        put("user_email", user.email)
                        put("user_name", user.name)
                        put("rating", rating.toDouble())
                        put("timestamp", System.currentTimeMillis())
                    }.toString()
                    val headers = mapOf(
                        "apikey" to SUPABASE_ANON_KEY,
                        "Authorization" to "Bearer $SUPABASE_ANON_KEY",
                        "Prefer" to "resolution=merge-duplicates"
                    )
                    executeHttpRequest(url, "POST", body, headers)
                } catch (e: Throwable) {
                    Log.e(TAG, "Supabase rating write error", e)
                }
            } else {
                try {
                    val url = "$RTDB_REST_BASE/mediaRatings/$docId.json?key=$FIREBASE_API_KEY"
                    val body = JSONObject().apply {
                        put("docId", docId)
                        put("imdbId", imdbId)
                        put("userId", finalUid)
                        put("userEmail", user.email)
                        put("userName", user.name)
                        put("rating", rating.toDouble())
                        put("timestamp", System.currentTimeMillis())
                    }.toString()
                    executeHttpRequest(url, "PUT", body)
                } catch (e: Throwable) {
                    Log.e(TAG, "Firebase rating write error", e)
                }
            }
        }

        onSuccess()
    }

    fun deleteComment(commentId: String) {
        _commentsState.value = _commentsState.value.filter { it.id != commentId }
        coroutineScope.launch(Dispatchers.IO) {
            commentDao.deleteComment(commentId)
            if (isSupabaseConfigured()) {
                try {
                    val url = "$SUPABASE_URL/rest/v1/media_comments?id=eq.$commentId"
                    val headers = mapOf(
                        "apikey" to SUPABASE_ANON_KEY,
                        "Authorization" to "Bearer $SUPABASE_ANON_KEY"
                    )
                    executeHttpRequest(url, "DELETE", headers = headers)
                } catch (e: Throwable) {
                    Log.e(TAG, "Supabase delete comment error", e)
                }
            } else {
                try {
                    val url = "$RTDB_REST_BASE/mediaComments/$commentId.json?key=$FIREBASE_API_KEY"
                    executeHttpRequest(url, "DELETE")
                } catch (e: Throwable) {
                    Log.e(TAG, "Firebase delete comment error", e)
                }
            }
        }
    }

    fun detach() {
        currentActiveImdbId = null
        currentActiveUserEmail = null
    }

    private fun executeHttpRequest(
        urlStr: String,
        method: String = "GET",
        jsonBody: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    outputStream.use { os ->
                        os.write(jsonBody.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "HTTP $responseCode on $method $urlStr: $err")
                throw java.io.IOException("HTTP $responseCode: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error on $method $urlStr", e)
            throw e
        } finally {
            connection?.disconnect()
        }
    }
}
