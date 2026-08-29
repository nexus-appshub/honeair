package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_comments")
data class MediaCommentEntity(
    @PrimaryKey
    val id: String,
    val imdbId: String,
    val userEmail: String,
    val userName: String,
    val userAvatar: String,
    val text: String,
    val rating: Float = 0f, // e.g. 5.0
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val parentId: String = "",
    val replyToUserName: String = "",
    val likedByCsv: String = ""
)

data class MediaComment(
    val id: String = "",
    val imdbId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val text: String = "",
    val rating: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val parentId: String = "",
    val replyToUserName: String = "",
    val likedBy: List<String> = emptyList()
) {
    fun toEntity(): MediaCommentEntity = MediaCommentEntity(
        id = id,
        imdbId = imdbId,
        userEmail = userEmail,
        userName = userName,
        userAvatar = userAvatar,
        text = text,
        rating = rating,
        timestamp = timestamp,
        likesCount = likesCount,
        parentId = parentId,
        replyToUserName = replyToUserName,
        likedByCsv = likedBy.joinToString(",")
    )

    companion object {
        fun fromEntity(entity: MediaCommentEntity): MediaComment = MediaComment(
            id = entity.id,
            imdbId = entity.imdbId,
            userEmail = entity.userEmail,
            userName = entity.userName,
            userAvatar = entity.userAvatar,
            text = entity.text,
            rating = entity.rating,
            timestamp = entity.timestamp,
            likesCount = entity.likesCount,
            parentId = entity.parentId,
            replyToUserName = entity.replyToUserName,
            likedBy = if (entity.likedByCsv.isBlank()) emptyList() else entity.likedByCsv.split(",")
        )
    }
}

data class MediaRatingSummary(
    val imdbId: String = "",
    val averageRating: Float = 0f,
    val totalRatings: Int = 0,
    val userRating: Float = 0f
)
