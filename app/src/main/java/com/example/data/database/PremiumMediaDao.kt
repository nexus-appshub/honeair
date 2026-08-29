package com.example.data.database
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PremiumMediaDao {
    @Query("SELECT * FROM premium_media")
    fun getAllPremiumMedia(): Flow<List<PremiumMediaEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPremiumMedia(media: PremiumMediaEntity)
    @Query("DELETE FROM premium_media WHERE id = :id")
    suspend fun deletePremiumMedia(id: String)
    @Query("SELECT EXISTS(SELECT 1 FROM premium_media WHERE id = :id LIMIT 1)")
    fun isPremiumMedia(id: String): Flow<Boolean>
}
