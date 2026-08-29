package com.example.data.database
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registered_users")
data class RegisteredUserEntity(
    @PrimaryKey val email: String,
    val isRestricted: Boolean = false,
    val isBanned: Boolean = false,
    val lastLogin: Long = System.currentTimeMillis()
)
