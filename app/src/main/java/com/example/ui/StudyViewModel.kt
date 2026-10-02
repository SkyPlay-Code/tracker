package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiVideoService
import com.example.data.api.VideoAnalysisResult
import com.example.data.local.AppDatabase
import com.example.data.local.DailyStatEntity
import com.example.data.local.StudySessionEntity
import com.example.data.repository.StudyRepository
import com.example.service.YouTubeTrackingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserProfile(
    val name: String = "Swayam Sidh",
    val email: String = "swayamsidh7@gmail.com",
    val gradeLevel: String = "Class 12th",
    val targetStudyMinutes: Int = 240 // 4 hours
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = StudyRepository(db.studyDao())
    val geminiService = GeminiVideoService()

    // User Profile state
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    // Live Tracking states
    val isAccessibilityConnected = YouTubeTrackingManager.isAccessibilityConnected
    val isTrackingActive = YouTubeTrackingManager.isTrackingActive
    val currentVideoTitle = YouTubeTrackingManager.currentVideoTitle
    val currentSubject = YouTubeTrackingManager.currentSubject
    val isCurrentShorts = YouTubeTrackingManager.isCurrentShorts
    val isCurrentStudy = YouTubeTrackingManager.isCurrentStudy
    val activeSessionSeconds = YouTubeTrackingManager.activeSessionSeconds
    val liveDistractionWarning = YouTubeTrackingManager.liveDistractionWarning

    // Room Database Flows
    val allSessions: StateFlow<List<StudySessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayDailyStat: StateFlow<DailyStatEntity?> = repository.getTodayDailyStat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Gemini Video Analysis state
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<VideoAnalysisResult?>(null)
    val analysisResult: StateFlow<VideoAnalysisResult?> = _analysisResult.asStateFlow()

    private val _analysisErrorMessage = MutableStateFlow<String?>(null)
    val analysisErrorMessage: StateFlow<String?> = _analysisErrorMessage.asStateFlow()

    init {
        // Seed helpful initial demonstration data for Class 12 student if empty
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getSessionById(1)
            if (existing == null) {
                seedInitialData()
            }
        }
    }

    private suspend fun seedInitialData() {
        val now = System.currentTimeMillis()
        // Physics study session
        repository.logSession(
            videoTitle = "Class 12 Physics: Electrostatics Full Chapter One Shot Revision",
            subject = "Physics",
            durationSeconds = 2400, // 40 mins
            isShorts = false,
            isStudy = true,
            aiSummary = "Covered Coulomb's law, electric field lines, electric potential, and capacitance formulas.",
            keyConcepts = "Coulomb's Law, Gauss's Theorem, Capacitors in series & parallel"
        )
        // Distraction on Shorts
        repository.logSession(
            videoTitle = "Viral Minecraft Parkour Storytime #shorts",
            subject = "YouTube Shorts (Wasted)",
            durationSeconds = 1260, // 21 mins
            isShorts = true,
            isStudy = false,
            aiSummary = "Time wasted scrolling through gaming and storytime Shorts feed.",
            keyConcepts = "Dopamine loop distraction"
        )
        // Chemistry study session
        repository.logSession(
            videoTitle = "Class 12 Organic Chemistry: Haloalkanes & Haloarenes Reactions",
            subject = "Chemistry",
            durationSeconds = 1920, // 32 mins
            isShorts = false,
            isStudy = true,
            aiSummary = "SN1 vs SN2 reaction mechanisms, nucleophilic substitution, stereochemistry.",
            keyConcepts = "SN1, SN2, Markovnikov rule, Inversion of configuration"
        )
        // Another distraction
        repository.logSession(
            videoTitle = "Most Satisfying Kinetic Sand ASMR #shorts",
            subject = "YouTube Shorts (Wasted)",
            durationSeconds = 900, // 15 mins
            isShorts = true,
            isStudy = false,
            aiSummary = "Endless scrolling session on YouTube Shorts.",
            keyConcepts = "Shorts distraction"
        )
    }

    fun analyzeVideoWithGemini(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisErrorMessage.value = null
            _analysisResult.value = null
            try {
                val result = geminiService.analyzeVideoWithGeminiPro(title, _userProfile.value.gradeLevel)
                _analysisResult.value = result
            } catch (e: Exception) {
                _analysisErrorMessage.value = "Failed to analyze video: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearAnalysis() {
        _analysisResult.value = null
        _analysisErrorMessage.value = null
    }

    fun simulateVideoPlayback(title: String, isShorts: Boolean, subject: String, isStudy: Boolean) {
        YouTubeTrackingManager.simulatePlayback(
            title = title,
            isShorts = isShorts,
            subject = subject,
            isStudy = isStudy,
            repository = repository
        )
    }

    fun stopActiveTracking() {
        YouTubeTrackingManager.stopTracking(repository)
    }

    fun flushActiveSession() {
        YouTubeTrackingManager.flushSessionToDatabase(repository)
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    fun updateUserProfile(name: String, email: String, grade: String, targetMinutes: Int) {
        _userProfile.value = UserProfile(
            name = name,
            email = email,
            gradeLevel = grade,
            targetStudyMinutes = targetMinutes
        )
    }
}
