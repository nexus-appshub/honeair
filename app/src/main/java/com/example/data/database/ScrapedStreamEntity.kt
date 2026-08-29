package com.example.data.database

import androidx.room.*

@Entity(tableName = "scraped_streams")
data class ScrapedStreamEntity(
    @PrimaryKey val id: String, // format: "tmdbId-season-episode"
    val streamUrl: String,
    val headersJson: String, // map serialized as JSON
    val referer: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ScrapedStreamDao {
    @Query("SELECT * FROM scraped_streams WHERE id = :id LIMIT 1")
    suspend fun getStreamById(id: String): ScrapedStreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: ScrapedStreamEntity)

    @Query("DELETE FROM scraped_streams WHERE id = :id")
    suspend fun deleteStreamById(id: String)

    @Query("DELETE FROM scraped_streams WHERE timestamp < :expiryTime")
    suspend fun deleteExpiredStreams(expiryTime: Long)

    @Query("DELETE FROM scraped_streams")
    suspend fun clearAllCachedStreams()
}
