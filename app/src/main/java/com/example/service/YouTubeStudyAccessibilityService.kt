package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.api.GeminiVideoService
import com.example.data.local.AppDatabase
import com.example.data.repository.StudyRepository

class YouTubeStudyAccessibilityService : AccessibilityService() {

    private val TAG = "StudyAccessibility"
    private lateinit var repository: StudyRepository
    private val geminiService = GeminiVideoService()

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        repository = StudyRepository(db.studyDao())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "YouTubeStudyAccessibilityService Connected!")
        YouTubeTrackingManager.setAccessibilityConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (!pkg.contains("youtube", ignoreCase = true)) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            val isShorts = detectIfShorts(rootNode)
            val title = extractVideoTitle(rootNode, isShorts)

            if (title.isNotBlank()) {
                Log.d(TAG, "Detected YouTube event - Title: $title | isShorts: $isShorts")
                YouTubeTrackingManager.onYouTubeContentDetected(
                    rawTitle = title,
                    isShorts = isShorts,
                    repository = repository,
                    geminiService = geminiService
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting YouTube accessibility tree", e)
        }
    }

    private fun detectIfShorts(root: AccessibilityNodeInfo): Boolean {
        // Search for known shorts identifiers
        val shortsNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/shorts_player")
        if (shortsNodes.isNotEmpty()) return true

        val reelNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_recycler")
        if (reelNodes.isNotEmpty()) return true

        val soundButtonNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/sound_button")
        if (soundButtonNodes.isNotEmpty()) return true

        // Check if any visible text contains "#shorts" or "Shorts" as a primary header
        val textMatches = root.findAccessibilityNodeInfosByText("Shorts")
        if (textMatches.isNotEmpty()) {
            for (node in textMatches) {
                if (node.isSelected || node.isFocused) {
                    return true
                }
            }
        }

        return false
    }

    private fun extractVideoTitle(root: AccessibilityNodeInfo, isShorts: Boolean): String {
        if (isShorts) {
            // Find shorts description or title
            val descNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title")
            for (node in descNodes) {
                val t = node.text?.toString()?.trim()
                if (!t.isNullOrBlank()) return t
            }
            val reelTitleNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_title")
            for (node in reelTitleNodes) {
                val t = node.text?.toString()?.trim()
                if (!t.isNullOrBlank()) return t
            }
            return "YouTube Shorts Feed"
        }

        // Standard YouTube video title view IDs
        val titleViewIds = listOf(
            "com.google.android.youtube:id/video_title",
            "com.google.android.youtube:id/title",
            "com.google.android.youtube:id/watch_title",
            "com.google.android.youtube:id/player_video_title"
        )

        for (id in titleViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrBlank() && text.length > 4) {
                    return text
                }
            }
        }

        // Fallback: search for candidate text nodes that look like study or video titles
        return findLikelyTitleFromHierarchy(root) ?: ""
    }

    private fun findLikelyTitleFromHierarchy(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val text = node.text?.toString()?.trim()

        if (!text.isNullOrBlank() && text.length in 12..120) {
            // Exclude common navigation labels
            val lower = text.lowercase()
            val isIgnored = lower == "home" || lower == "subscriptions" || lower == "you" ||
                    lower == "library" || lower == "explore" || lower == "search" ||
                    lower.contains("views") || lower.contains("subscribers") ||
                    lower.contains("ago") || lower.contains("comments")
            if (!isIgnored) {
                return text
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findLikelyTitleFromHierarchy(child)
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "YouTubeStudyAccessibilityService Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        YouTubeTrackingManager.setAccessibilityConnected(false)
    }
}
