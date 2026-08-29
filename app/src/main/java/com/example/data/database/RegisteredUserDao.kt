package com.example.data.database
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RegisteredUserDao {
    @Query("SELECT * FROM registered_users ORDER BY lastLogin DESC")
    fun getAllUsers(): Flow<List<RegisteredUserEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: RegisteredUserEntity)
    @Query("UPDATE registered_users SET isRestricted = :isRestricted WHERE email = :email")
    suspend fun updateRestriction(email: String, isRestricted: Boolean)
    @Query("UPDATE registered_users SET isBanned = :isBanned WHERE email = :email")
    suspend fun updateBanStatus(email: String, isBanned: Boolean)
    @Query("DELETE FROM registered_users WHERE email = :email")
    suspend fun deleteUser(email: String)
}
