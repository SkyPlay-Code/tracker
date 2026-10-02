package com.example.data.api

data class VideoAnalysisResult(
    val title: String,
    val subject: String,
    val isStudyRelated: Boolean,
    val isShortsOrDistraction: Boolean,
    val topic: String,
    val confidenceScore: Int, // 0 - 100
    val summary: String,
    val keyFormulasAndConcepts: List<String>,
    val studyRetentionQuiz: List<QuizQuestion>,
    val studyTips: String,
    val timeWastedNotice: String? = null
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)
