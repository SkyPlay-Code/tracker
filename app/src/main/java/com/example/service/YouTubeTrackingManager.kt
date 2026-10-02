package com.example.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.api.GeminiVideoService
import com.example.data.model.StudySubject
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object YouTubeTrackingManager {
    private const val TAG = "YouTubeTrackingManager"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private val _currentVideoTitle = MutableStateFlow("No YouTube video detected")
    val currentVideoTitle: StateFlow<String> = _currentVideoTitle.asStateFlow()

    private val _currentSubject = MutableStateFlow("Idle")
    val currentSubject: StateFlow<String> = _currentSubject.asStateFlow()

    private val _isCurrentShorts = MutableStateFlow(false)
    val isCurrentShorts: StateFlow<Boolean> = _isCurrentShorts.asStateFlow()

    private val _isCurrentStudy = MutableStateFlow(false)
    val isCurrentStudy: StateFlow<Boolean> = _isCurrentStudy.asStateFlow()

    private val _activeSessionSeconds = MutableStateFlow(0L)
    val activeSessionSeconds: StateFlow<Long> = _activeSessionSeconds.asStateFlow()

    private val _liveDistractionWarning = MutableStateFlow<String?>(null)
    val liveDistractionWarning: StateFlow<String?> = _liveDistractionWarning.asStateFlow()

    private var activeTickerJob: Job? = null
    private var lastRecordedTitle: String = ""
    private var lastRecordedSubject: String = ""
    private var lastIsShorts: Boolean = false
    private var lastIsStudy: Boolean = false
    private var sessionElapsedSeconds: Long = 0L

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
        Log.d(TAG, "Accessibility service connection state: $connected")
    }

    fun onYouTubeContentDetected(
        rawTitle: String,
        isShorts: Boolean,
        repository: StudyRepository,
        geminiService: GeminiVideoService
    ) {
        val title = rawTitle.trim()
        if (title.isBlank()) return

        // If title is the same and status is the same, continue existing session
        if (title == lastRecordedTitle && isShorts == lastIsShorts) {
            _isTrackingActive.value = true
            return
        }

        // Previous video finished or switched, persist accumulated session if > 3 seconds
        if (sessionElapsedSeconds >= 3 && lastRecordedTitle.isNotBlank()) {
            flushSessionToDatabase(repository)
        }

        lastRecordedTitle = title
        lastIsShorts = isShorts
        sessionElapsedSeconds = 0L
        _activeSessionSeconds.value = 0L
        _currentVideoTitle.value = title
        _isCurrentShorts.value = isShorts
        _isTrackingActive.value = true

        if (isShorts) {
            _currentSubject.value = "YouTube Shorts (Wasted)"
            _isCurrentStudy.value = false
            lastIsStudy = false
            lastRecordedSubject = "YouTube Shorts (Wasted)"
            _liveDistractionWarning.value = "⚠️ Distraction detected! You are currently watching YouTube Shorts."
            startOrEnsureTicker(repository)
        } else {
            _liveDistractionWarning.value = null
            // Classify via Gemini
            scope.launch {
                val classification = geminiService.classifyVideo(title, isShortsDetected = false)
                _currentSubject.value = classification.subject
                _isCurrentStudy.value = classification.isStudy
                lastIsStudy = classification.isStudy
                lastRecordedSubject = classification.subject

                if (!classification.isStudy) {
                    _liveDistractionWarning.value = "⚠️ Unrelated video detected: Classified as '${classification.subject}' (Wasted focus)."
                } else {
                    _liveDistractionWarning.value = null
                }
                startOrEnsureTicker(repository)
            }
        }
    }

    private fun startOrEnsureTicker(repository: StudyRepository) {
        if (activeTickerJob?.isActive == true) return

        activeTickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                if (_isTrackingActive.value) {
                    sessionElapsedSeconds++
                    _activeSessionSeconds.value = sessionElapsedSeconds

                    // Periodically commit every 15 seconds to ensure real-time persistence
                    if (sessionElapsedSeconds % 15L == 0L && lastRecordedTitle.isNotBlank()) {
                        flushSessionToDatabase(repository)
                    }
                }
            }
        }
    }

    fun flushSessionToDatabase(repository: StudyRepository) {
        val titleToSave = lastRecordedTitle
        val secondsToSave = sessionElapsedSeconds
        val subjectToSave = lastRecordedSubject.ifBlank { "General Study" }
        val isShortsToSave = lastIsShorts
        val isStudyToSave = lastIsStudy

        if (secondsToSave <= 0 || titleToSave.isBlank()) return

        scope.launch(Dispatchers.IO) {
            repository.logSession(
                videoTitle = titleToSave,
                videoUrlOrId = "",
                subject = subjectToSave,
                durationSeconds = secondsToSave,
                isShorts = isShortsToSave,
                isStudy = isStudyToSave,
                aiSummary = if (isStudyToSave) "Study session for $subjectToSave" else "Time spent on distraction / Shorts",
                keyConcepts = null,
                relevanceScore = if (isStudyToSave) 95 else 5
            )
        }
    }

    fun stopTracking(repository: StudyRepository) {
        _isTrackingActive.value = false
        if (sessionElapsedSeconds > 0 && lastRecordedTitle.isNotBlank()) {
            flushSessionToDatabase(repository)
        }
        sessionElapsedSeconds = 0L
        _activeSessionSeconds.value = 0L
        _currentVideoTitle.value = "Tracking Paused"
        _currentSubject.value = "Idle"
        _liveDistractionWarning.value = null
        activeTickerJob?.cancel()
        activeTickerJob = null
    }

    /**
     * Simulator method: Allows testing the YouTube tracking directly inside the app or emulator
     */
    fun simulatePlayback(
        title: String,
        isShorts: Boolean,
        subject: String,
        isStudy: Boolean,
        repository: StudyRepository
    ) {
        // Flush previous
        if (sessionElapsedSeconds >= 2 && lastRecordedTitle.isNotBlank()) {
            flushSessionToDatabase(repository)
        }

        lastRecordedTitle = title
        lastRecordedSubject = subject
        lastIsShorts = isShorts
        lastIsStudy = isStudy
        sessionElapsedSeconds = 0L
        _activeSessionSeconds.value = 0L
        _currentVideoTitle.value = title
        _currentSubject.value = subject
        _isCurrentShorts.value = isShorts
        _isCurrentStudy.value = isStudy
        _isTrackingActive.value = true

        if (isShorts) {
            _liveDistractionWarning.value = "⚠️ Distraction detected! YouTube Shorts feed is active."
        } else if (!isStudy) {
            _liveDistractionWarning.value = "⚠️ Entertainment detected: '${subject}' is not counted towards study."
        } else {
            _liveDistractionWarning.value = null
        }

        startOrEnsureTicker(repository)
    }
}
