package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class StudySubject(
    val displayName: String,
    val hexColor: Long,
    val isDistraction: Boolean = false
) {
    PHYSICS("Physics", 0xFF2979FF),
    CHEMISTRY("Chemistry", 0xFF00E676),
    MATHEMATICS("Mathematics", 0xFFFF9100),
    BIOLOGY("Biology", 0xFF00B0FF),
    COMPUTER_SCIENCE("Computer Science", 0xFF7C4DFF),
    ENGLISH("English", 0xFFFF4081),
    GENERAL_STUDY("General Study", 0xFF00BFA5),
    SHORTS_DISTRACTION("YouTube Shorts (Wasted)", 0xFFFF1744, isDistraction = true),
    ENTERTAINMENT("Entertainment / Non-Study", 0xFFFF5252, isDistraction = true);

    companion object {
        fun fromName(name: String): StudySubject {
            val clean = name.trim().lowercase()
            return when {
                clean.contains("short") -> SHORTS_DISTRACTION
                clean.contains("physic") || clean.contains("mechanic") || clean.contains("optics") || clean.contains("electro") -> PHYSICS
                clean.contains("chem") || clean.contains("organic") || clean.contains("inorganic") -> CHEMISTRY
                clean.contains("math") || clean.contains("calculus") || clean.contains("algebra") || clean.contains("trig") || clean.contains("integration") -> MATHEMATICS
                clean.contains("bio") || clean.contains("botany") || clean.contains("zoology") || clean.contains("genetics") -> BIOLOGY
                clean.contains("comp") || clean.contains("code") || clean.contains("python") || clean.contains("java") || clean.contains("sql") -> COMPUTER_SCIENCE
                clean.contains("eng") || clean.contains("grammar") || clean.contains("literature") -> ENGLISH
                clean.contains("game") || clean.contains("vlog") || clean.contains("music") || clean.contains("funny") || clean.contains("meme") || clean.contains("trailer") || clean.contains("roast") -> ENTERTAINMENT
                else -> GENERAL_STUDY
            }
        }
    }
}
