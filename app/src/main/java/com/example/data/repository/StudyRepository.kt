package com.example.data.repository

import com.example.data.local.DailyStatEntity
import com.example.data.local.StudyDao
import com.example.data.local.StudySessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudyRepository(private val dao: StudyDao) {

    val allSessions: Flow<List<StudySessionEntity>> = dao.getAllSessions()

    fun getTodayDateKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getTodayStartTimestamp(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getTodaySessions(): Flow<List<StudySessionEntity>> {
        return dao.getSessionsSince(getTodayStartTimestamp())
    }

    fun getTodayDailyStat(): Flow<DailyStatEntity?> {
        return dao.getDailyStat(getTodayDateKey())
    }

    suspend fun logSession(
        videoTitle: String,
        videoUrlOrId: String = "",
        subject: String,
        durationSeconds: Long,
        isShorts: Boolean,
        isStudy: Boolean,
        aiSummary: String? = null,
        keyConcepts: String? = null,
        relevanceScore: Int = if (isStudy) 90 else 10
    ): Long {
        val entity = StudySessionEntity(
            videoTitle = videoTitle,
            videoUrlOrId = videoUrlOrId,
            subject = subject,
            durationSeconds = durationSeconds,
            isShorts = isShorts,
            isStudy = isStudy,
            timestamp = System.currentTimeMillis(),
            aiSummary = aiSummary,
            keyConcepts = keyConcepts,
            relevanceScore = relevanceScore
        )
        val id = dao.insertSession(entity)
        updateDailyAggregates()
        return id
    }

    suspend fun updateDailyAggregates() {
        val todaySessions = dao.getSessionsSince(getTodayStartTimestamp()).firstOrNull() ?: emptyList()
        var totalStudy = 0L
        var totalWasted = 0L
        var shortsCount = 0
        val subjectMap = mutableMapOf<String, Long>()

        for (session in todaySessions) {
            if (session.isStudy) {
                totalStudy += session.durationSeconds
                val current = subjectMap.getOrDefault(session.subject, 0L)
                subjectMap[session.subject] = current + session.durationSeconds
            } else {
                totalWasted += session.durationSeconds
                if (session.isShorts) {
                    shortsCount++
                }
            }
        }

        val dominantSubject = subjectMap.maxByOrNull { it.value }?.key ?: "Physics"

        val stat = DailyStatEntity(
            dateKey = getTodayDateKey(),
            totalStudySeconds = totalStudy,
            totalWastedSeconds = totalWasted,
            shortsCount = shortsCount,
            videosWatchedCount = todaySessions.size,
            dominantSubject = dominantSubject
        )
        dao.insertOrUpdateDailyStat(stat)
    }

    suspend fun deleteSession(id: Long) {
        dao.deleteSessionById(id)
        updateDailyAggregates()
    }

    suspend fun clearHistory() {
        dao.clearAllSessions()
        updateDailyAggregates()
    }

    suspend fun getSessionById(id: Long): StudySessionEntity? {
        return dao.getSessionById(id)
    }
}
