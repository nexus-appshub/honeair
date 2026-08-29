package com.example.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Entities
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val url: String,
    val name: String,
    val logo: String,
    val groupName: String,
    val tvgId: String
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val name: String,
    val logo: String,
    val groupName: String,
    val timestamp: Long
)

@Entity(tableName = "admin_logs")
data class AdminLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "ERROR", "ALERT", "INFO"
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

// DAOs
@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY name ASC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url LIMIT 1)")
    fun isFavorite(url: String): Flow<Boolean>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 50")
    fun getWatchHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteHistoryByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}

@Dao
interface AdminLogDao {
    @Query("SELECT * FROM admin_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<AdminLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AdminLogEntity)

    @Query("DELETE FROM admin_logs")
    suspend fun clearAllLogs()
}

@Entity(tableName = "media_history")
data class MediaHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val imageUrl: String,
    val rating: String,
    val year: String,
    val description: String,
    val streamUrl: String,
    val episodes: String,
    val isStreamable: Boolean,
    val imdbId: String?,
    val type: String,
    val timestamp: Long
)

@Entity(tableName = "media_favorites")
data class MediaFavoriteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val imageUrl: String,
    val rating: String,
    val year: String,
    val description: String,
    val streamUrl: String,
    val episodes: String,
    val isStreamable: Boolean,
    val imdbId: String?,
    val type: String
)

@Dao
interface MediaHistoryDao {
    @Query("SELECT * FROM media_history ORDER BY timestamp DESC LIMIT 50")
    fun getMediaHistory(): Flow<List<MediaHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaHistory(history: MediaHistoryEntity)

    @Query("DELETE FROM media_history WHERE id = :id")
    suspend fun deleteMediaHistoryById(id: String)

    @Query("DELETE FROM media_history")
    suspend fun clearMediaHistory()
}

@Dao
interface MediaFavoriteDao {
    @Query("SELECT * FROM media_favorites ORDER BY title ASC")
    fun getAllMediaFavorites(): Flow<List<MediaFavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFavorite(favorite: MediaFavoriteEntity)

    @Delete
    suspend fun deleteMediaFavorite(favorite: MediaFavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM media_favorites WHERE id = :id LIMIT 1)")
    fun isMediaFavorite(id: String): Flow<Boolean>
}

// Database
@Database(
    entities = [
        FavoriteEntity::class, 
        HistoryEntity::class, 
        AdminLogEntity::class, 
        MediaHistoryEntity::class, 
        MediaFavoriteEntity::class,
        PremiumMediaEntity::class,
        RegisteredUserEntity::class,
        com.example.data.model.MediaCommentEntity::class,
        ScrapedStreamEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun adminLogDao(): AdminLogDao
    abstract fun mediaHistoryDao(): MediaHistoryDao
    abstract fun mediaFavoriteDao(): MediaFavoriteDao
    abstract fun premiumMediaDao(): PremiumMediaDao
    abstract fun registeredUserDao(): RegisteredUserDao
    abstract fun mediaCommentDao(): MediaCommentDao
    abstract fun scrapedStreamDao(): ScrapedStreamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "home_air_tv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
