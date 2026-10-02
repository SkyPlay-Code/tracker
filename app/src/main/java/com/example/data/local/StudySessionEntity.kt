package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoTitle: String,
    val videoUrlOrId: String = "",
    val subject: String, // e.g. "Physics", "Chemistry", "Mathematics", "Biology", "Distraction / Shorts"
    val durationSeconds: Long,
    val isShorts: Boolean,
    val isStudy: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val aiSummary: String? = null,
    val keyConcepts: String? = null, // JSON or comma-separated
    val relevanceScore: Int = 100 // 0 to 100%
)
