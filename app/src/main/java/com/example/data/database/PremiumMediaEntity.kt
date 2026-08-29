package com.example.data.database
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "premium_media")
data class PremiumMediaEntity(
    @PrimaryKey val id: String
)
