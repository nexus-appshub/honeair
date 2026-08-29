package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.MediaCommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaCommentDao {
    @Query("SELECT * FROM media_comments WHERE imdbId = :imdbId ORDER BY timestamp DESC")
    fun getCommentsForMedia(imdbId: String): Flow<List<MediaCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<MediaCommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: MediaCommentEntity)

    @Query("DELETE FROM media_comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: String)
}
