package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey
    val dateKey: String, // Format "YYYY-MM-DD"
    val totalStudySeconds: Long = 0,
    val totalWastedSeconds: Long = 0,
    val shortsCount: Int = 0,
    val videosWatchedCount: Int = 0,
    val dominantSubject: String = "None"
)
