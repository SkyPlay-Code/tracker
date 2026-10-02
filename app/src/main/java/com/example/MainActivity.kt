package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.StudyViewModel
import com.example.ui.components.FocusRealityCard
import com.example.ui.components.HeaderUserBar
import com.example.ui.components.LiveTrackingCard
import com.example.ui.components.ProfileEditDialog
import com.example.ui.components.RecentVideosList
import com.example.ui.components.SubjectBreakdownCard
import com.example.ui.components.VideoAnalyzerCard
import com.example.ui.theme.SmartStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartStudyTheme {
                SmartStudyApp()
            }
        }
    }
}

@Composable
fun SmartStudyApp(viewModel: StudyViewModel = viewModel()) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
    val isTrackingActive by viewModel.isTrackingActive.collectAsState()
    val currentVideoTitle by viewModel.currentVideoTitle.collectAsState()
    val currentSubject by viewModel.currentSubject.collectAsState()
    val isCurrentShorts by viewModel.isCurrentShorts.collectAsState()
    val isCurrentStudy by viewModel.isCurrentStudy.collectAsState()
    val activeSessionSeconds by viewModel.activeSessionSeconds.collectAsState()
    val liveDistractionWarning by viewModel.liveDistractionWarning.collectAsState()

    val todayDailyStat by viewModel.todayDailyStat.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()

    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val analysisErrorMessage by viewModel.analysisErrorMessage.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 1. Google Account & Accessibility Status
                item {
                    HeaderUserBar(
                        userProfile = userProfile,
                        isAccessibilityEnabled = isAccessibilityConnected,
                        onOpenProfileDialog = { showProfileDialog = true }
                    )
                }

                // 2. Focus vs. Wasted Reality Comparison Card
                item {
                    FocusRealityCard(
                        studySeconds = todayDailyStat?.totalStudySeconds ?: 0L,
                        wastedSeconds = todayDailyStat?.totalWastedSeconds ?: 0L,
                        shortsCount = todayDailyStat?.shortsCount ?: 0,
                        targetDailyMinutes = userProfile.targetStudyMinutes
                    )
                }

                // 3. Live YouTube Tracker & Simulation Control
                item {
                    LiveTrackingCard(
                        isTrackingActive = isTrackingActive,
                        videoTitle = currentVideoTitle,
                        currentSubject = currentSubject,
                        isCurrentShorts = isCurrentShorts,
                        isCurrentStudy = isCurrentStudy,
                        sessionSeconds = activeSessionSeconds,
                        distractionWarning = liveDistractionWarning,
                        onSimulate = { title, isShorts, subject, isStudy ->
                            viewModel.simulateVideoPlayback(title, isShorts, subject, isStudy)
                        },
                        onStopTracking = {
                            viewModel.stopActiveTracking()
                        }
                    )
                }

                // 4. Subject-Wise Breakdown
                item {
                    SubjectBreakdownCard(sessions = allSessions)
                }

                // 5. Gemini 3.1 Pro Video Content Understanding & Retention Quiz
                item {
                    VideoAnalyzerCard(
                        analysisResult = analysisResult,
                        isAnalyzing = isAnalyzing,
                        errorMessage = analysisErrorMessage,
                        onAnalyzeVideo = { title ->
                            viewModel.analyzeVideoWithGemini(title)
                        },
                        onClearAnalysis = {
                            viewModel.clearAnalysis()
                        }
                    )
                }

                // 6. Tracked YouTube History & Audit Log
                item {
                    RecentVideosList(
                        sessions = allSessions,
                        onAnalyzeVideo = { title ->
                            viewModel.analyzeVideoWithGemini(title)
                        },
                        onDeleteSession = { id ->
                            viewModel.deleteSession(id)
                        },
                        onClearAll = {
                            viewModel.clearAllHistory()
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            currentProfile = userProfile,
            onDismiss = { showProfileDialog = false },
            onSave = { name, email, grade, targetMinutes ->
                viewModel.updateUserProfile(name, email, grade, targetMinutes)
                showProfileDialog = false
            }
        )
    }
}
