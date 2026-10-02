package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class FastClassification(
    val subject: String,
    val isStudy: Boolean,
    val isShorts: Boolean,
    val topic: String,
    val confidence: Int
)

class GeminiVideoService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Fast classification using gemini-3.5-flash or smart heuristics
     */
    suspend fun classifyVideo(title: String, isShortsDetected: Boolean): FastClassification = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val cleanTitle = title.trim()

        if (isShortsDetected || cleanTitle.contains("#shorts", ignoreCase = true) || cleanTitle.contains("/shorts/", ignoreCase = true)) {
            return@withContext FastClassification(
                subject = "YouTube Shorts (Wasted)",
                isStudy = false,
                isShorts = true,
                topic = "Short Form Entertainment",
                confidence = 98
            )
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext fallbackFastClassification(cleanTitle)
        }

        try {
            val prompt = """
                You are a smart high school & college study tracker assistant.
                Classify the following YouTube video title:
                "$cleanTitle"

                Determine:
                1. Is this video educational/academic study material (e.g. Physics, Chemistry, Maths, Biology, Computer Science, English, Exam Prep, Lectures)? Or is it entertainment/vlog/gaming/comedy/distraction?
                2. If study, what is the specific academic subject? (Choose from: Physics, Chemistry, Mathematics, Biology, Computer Science, English, or General Study)
                3. Exact chapter or topic name.

                Respond in STRICT JSON format:
                {
                   "isStudy": true/false,
                   "subject": "Physics" | "Chemistry" | "Mathematics" | "Biology" | "Computer Science" | "English" | "General Study" | "Entertainment / Non-Study",
                   "topic": "Brief topic name",
                   "confidence": 95
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("GeminiVideoService", "Flash API error: ${response.code}")
                    return@withContext fallbackFastClassification(cleanTitle)
                }

                val responseBody = response.body?.string() ?: return@withContext fallbackFastClassification(cleanTitle)
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!text.isNullOrBlank()) {
                    val parsed = JSONObject(text.trim())
                    val isStudy = parsed.optBoolean("isStudy", true)
                    val subject = parsed.optString("subject", "General Study")
                    val topic = parsed.optString("topic", cleanTitle)
                    val confidence = parsed.optInt("confidence", 85)

                    return@withContext FastClassification(
                        subject = subject,
                        isStudy = isStudy,
                        isShorts = false,
                        topic = topic,
                        confidence = confidence
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiVideoService", "Failed to classify via Gemini", e)
        }

        fallbackFastClassification(cleanTitle)
    }

    /**
     * Deep video intelligence analysis using gemini-3.1-pro-preview
     * Extracts key formulas, concepts, syllabus relevance, and retention quiz.
     */
    suspend fun analyzeVideoWithGeminiPro(
        videoTitle: String,
        academicClass: String = "Class 12th"
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val cleanTitle = videoTitle.trim()

        if (cleanTitle.contains("#shorts", ignoreCase = true) || cleanTitle.contains("shorts", ignoreCase = true)) {
            return@withContext VideoAnalysisResult(
                title = cleanTitle,
                subject = "YouTube Shorts (Wasted)",
                isStudyRelated = false,
                isShortsOrDistraction = true,
                topic = "Social Media Distraction",
                confidenceScore = 99,
                summary = "This was identified as a short-form YouTube distraction. YouTube Shorts provide rapid dopamine spikes with negligible educational retention. Any time spent on this was automatically logged as wasted study time.",
                keyFormulasAndConcepts = listOf(
                    "Dopamine loop of short-form content",
                    "Cognitive fragmentation & context switching cost"
                ),
                studyRetentionQuiz = emptyList(),
                studyTips = "Close YouTube Shorts immediately. Return to your targeted Class 12 syllabus schedule.",
                timeWastedNotice = "⚠️ Distraction Alert: Shorts watch time has been deducted from your effective focus score!"
            )
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = """
                    You are an expert high school and competitive exam academic mentor (specializing in $academicClass and CBSE/JEE/NEET).
                    Analyze this YouTube study video:
                    Title: "$cleanTitle"

                    Provide a comprehensive academic breakdown for a student studying for $academicClass.
                    Include:
                    1. Subject (Physics, Chemistry, Mathematics, Biology, Computer Science, English, or Non-Study)
                    2. Whether this is genuinely study-related or entertainment distraction
                    3. Exact Chapter and Core Topic
                    4. Summary of the educational value and what the student will learn
                    5. 3 to 5 core Key Formulas, Theorems, or Key Concepts covered in this topic
                    6. 2 quick multiple-choice retention questions (each with 4 options and correct option index 0-3) to check if the student really grasped the concepts
                    7. Actionable study tip for this topic

                    Output MUST be valid JSON adhering to this structure:
                    {
                      "subject": "Physics",
                      "isStudyRelated": true,
                      "isShortsOrDistraction": false,
                      "topic": "Electrostatics: Gauss Law & Electric Field",
                      "confidenceScore": 95,
                      "summary": "Covers electrostatic flux and derivation of Gauss Law applications for spherical conductors.",
                      "keyFormulasAndConcepts": [
                        "Gauss Law: \u222e E\u22c5dA = q_enclosed / \u03b5_0",
                        "Electric field near infinite sheet: E = \u03c3 / (2\u03b5_0)",
                        "Flux through closed surface is independent of shape"
                      ],
                      "studyRetentionQuiz": [
                        {
                          "question": "What is the electric field inside a charged hollow conducting sphere?",
                          "options": ["Zero", "q / (4\u03c0\u03b5\u2080 r\u00b2)", "\u03c3 / \u03b5\u2080", "Infinite"],
                          "correctIndex": 0,
                          "explanation": "By Gauss law, enclosed charge is zero inside the cavity of conductor, so E = 0."
                        }
                      ],
                      "studyTips": "Derive Gauss law applications twice on blank paper without looking at notes."
                    }
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    }
                    put("contents", contentsArray)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)
                        put("responseMimeType", "application/json")
                    })
                }

                // Using gemini-3.1-pro-preview as strictly required
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (!responseBody.isNullOrBlank()) {
                            val jsonRoot = JSONObject(responseBody)
                            val candidates = jsonRoot.optJSONArray("candidates")
                            val text = candidates?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text")

                            if (!text.isNullOrBlank()) {
                                return@withContext parseVideoAnalysisJson(cleanTitle, text)
                            }
                        }
                    } else {
                        Log.w("GeminiVideoService", "Gemini 3.1 Pro returned: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiVideoService", "Error calling gemini-3.1-pro-preview", e)
            }
        }

        // High quality fallback analysis
        generateFallbackDeepAnalysis(cleanTitle, academicClass)
    }

    private fun parseVideoAnalysisJson(originalTitle: String, jsonStr: String): VideoAnalysisResult {
        try {
            val json = JSONObject(jsonStr.trim())
            val subject = json.optString("subject", "General Study")
            val isStudy = json.optBoolean("isStudyRelated", true)
            val isShorts = json.optBoolean("isShortsOrDistraction", false)
            val topic = json.optString("topic", originalTitle)
            val confidence = json.optInt("confidenceScore", 92)
            val summary = json.optString("summary", "Analysis completed for $originalTitle.")
            val studyTips = json.optString("studyTips", "Review core formulas and practice 5 textbook problems.")

            val formulas = mutableListOf<String>()
            val formulasJson = json.optJSONArray("keyFormulasAndConcepts")
            if (formulasJson != null) {
                for (i in 0 until formulasJson.length()) {
                    formulas.add(formulasJson.getString(i))
                }
            }

            val quiz = mutableListOf<QuizQuestion>()
            val quizJson = json.optJSONArray("studyRetentionQuiz")
            if (quizJson != null) {
                for (i in 0 until quizJson.length()) {
                    val qObj = quizJson.getJSONObject(i)
                    val qText = qObj.optString("question", "")
                    val correctIdx = qObj.optInt("correctIndex", 0)
                    val explanation = qObj.optString("explanation", "")
                    val optsJson = qObj.optJSONArray("options")
                    val options = mutableListOf<String>()
                    if (optsJson != null) {
                        for (j in 0 until optsJson.length()) {
                            options.add(optsJson.getString(j))
                        }
                    }
                    if (qText.isNotBlank() && options.isNotEmpty()) {
                        quiz.add(QuizQuestion(qText, options, correctIdx, explanation))
                    }
                }
            }

            return VideoAnalysisResult(
                title = originalTitle,
                subject = subject,
                isStudyRelated = isStudy,
                isShortsOrDistraction = isShorts,
                topic = topic,
                confidenceScore = confidence,
                summary = summary,
                keyFormulasAndConcepts = if (formulas.isEmpty()) listOf("Core theoretical principles of $topic") else formulas,
                studyRetentionQuiz = quiz,
                studyTips = studyTips,
                timeWastedNotice = if (!isStudy) "⚠️ This video is classified as entertainment/distraction and does not count as study time." else null
            )
        } catch (e: Exception) {
            Log.e("GeminiVideoService", "JSON parse error", e)
            return generateFallbackDeepAnalysis(originalTitle, "Class 12th")
        }
    }

    private fun fallbackFastClassification(title: String): FastClassification {
        val lower = title.lowercase()
        return when {
            lower.contains("short") || lower.contains("tiktok") || lower.contains("reel") ->
                FastClassification("YouTube Shorts (Wasted)", isStudy = false, isShorts = true, topic = "Shorts Distraction", confidence = 95)
            lower.contains("physic") || lower.contains("optics") || lower.contains("mechanic") || lower.contains("gravitation") || lower.contains("electro") || lower.contains("current") || lower.contains("magnet") ->
                FastClassification("Physics", isStudy = true, isShorts = false, topic = "Physics Lecture", confidence = 92)
            lower.contains("chem") || lower.contains("organic") || lower.contains("inorganic") || lower.contains("reaction") || lower.contains("equilibrium") || lower.contains("acid") || lower.contains("solution") ->
                FastClassification("Chemistry", isStudy = true, isShorts = false, topic = "Chemistry Session", confidence = 92)
            lower.contains("math") || lower.contains("calculus") || lower.contains("integration") || lower.contains("derivative") || lower.contains("matrix") || lower.contains("vector") || lower.contains("probability") ->
                FastClassification("Mathematics", isStudy = true, isShorts = false, topic = "Mathematics Problem Solving", confidence = 92)
            lower.contains("bio") || lower.contains("cell") || lower.contains("genetics") || lower.contains("dna") || lower.contains("plant") || lower.contains("human") || lower.contains("evolution") ->
                FastClassification("Biology", isStudy = true, isShorts = false, topic = "Biology Revision", confidence = 90)
            lower.contains("computer") || lower.contains("code") || lower.contains("python") || lower.contains("programming") || lower.contains("algorithm") || lower.contains("data structure") ->
                FastClassification("Computer Science", isStudy = true, isShorts = false, topic = "Computer Science & Coding", confidence = 90)
            lower.contains("game") || lower.contains("vlog") || lower.contains("funny") || lower.contains("roast") || lower.contains("challenge") || lower.contains("minecraft") || lower.contains("gta") ->
                FastClassification("Entertainment / Non-Study", isStudy = false, isShorts = false, topic = "Entertainment Distraction", confidence = 94)
            lower.contains("class 12") || lower.contains("ncert") || lower.contains("board exam") || lower.contains("one shot") || lower.contains("revision") || lower.contains("pyq") || lower.contains("lecture") ->
                FastClassification("General Study", isStudy = true, isShorts = false, topic = "Class 12 Exam Prep", confidence = 88)
            else ->
                FastClassification("General Study", isStudy = true, isShorts = false, topic = "Self-Study Video", confidence = 75)
        }
    }

    private fun generateFallbackDeepAnalysis(title: String, academicClass: String): VideoAnalysisResult {
        val fast = fallbackFastClassification(title)
        val isStudy = fast.isStudy

        val (summary, formulas, quiz, tip) = if (isStudy) {
            when (fast.subject) {
                "Physics" -> Quad(
                    "High-yield physics lecture covering problem solving and key principles for $academicClass.",
                    listOf(
                        "Force & Fields: F = q(E + v \u00d7 B)",
                        "Work-Energy theorem: W_net = \u0394K",
                        "Gauss's Law: \u222e E\u22c5dA = Q_enc / \u03b5_0",
                        "Faraday's Law: \u03b5 = -d\u03a6_B / dt"
                    ),
                    listOf(
                        QuizQuestion(
                            "What is the SI unit of electric flux?",
                            listOf("N\u22c5m\u00b2/C", "Volt", "Tesla", "Coulomb"),
                            0,
                            "Flux is Electric field (N/C) multiplied by Area (m²)."
                        ),
                        QuizQuestion(
                            "When a magnetic field is constant, the induced electromotive force (EMF) is:",
                            listOf("Zero", "Maximum", "Infinite", "Proportional to field area"),
                            0,
                            "Induced EMF depends on rate of change of magnetic flux (dΦ/dt). If field is constant, EMF = 0."
                        )
                    ),
                    "Practice 3 numerical problems from NCERT right after watching."
                )
                "Chemistry" -> Quad(
                    "Essential chemistry concepts and reaction mechanisms for $academicClass.",
                    listOf(
                        "Nernst Equation: E = E\u00b0 - (RT/nF) ln(Q)",
                        "Arrhenius Equation: k = A e^(-Ea / RT)",
                        "Raoult's Law: P = P\u00b0 \u00d7 \u03c7",
                        "Rate Law: Rate = k[A]^x[B]^y"
                    ),
                    listOf(
                        QuizQuestion(
                            "In an SN2 mechanism, the reaction proceeds with:",
                            listOf("Inversion of configuration", "Retention of configuration", "Racemization", "No change"),
                            0,
                            "SN2 involves backside attack causing Walden inversion."
                        )
                    ),
                    "Draw reaction flowcharts and name reactions by hand."
                )
                "Mathematics" -> Quad(
                    "Mathematical techniques, formulas, and step-by-step calculus/algebra problem solving.",
                    listOf(
                        "Integration by parts: \u222b u v dx = u\u222bv dx - \u222b(u' \u222bv dx) dx",
                        "Chain Rule: d/dx[f(g(x))] = f'(g(x)) \u00d7 g'(x)",
                        "Matrix Determinant: det(AB) = det(A) \u00d7 det(B)",
                        "Vector Dot Product: A \u22c5 B = |A||B| cos(\u03b8)"
                    ),
                    listOf(
                        QuizQuestion(
                            "What is \u222b sec\u00b2(x) dx?",
                            listOf("tan(x) + C", "sec(x) + C", "-tan(x) + C", "cos(x) + C"),
                            0,
                            "Derivative of tan(x) is sec²(x), so the anti-derivative is tan(x) + C."
                        )
                    ),
                    "Solve at least 5 previous year question papers (PYQs) for this topic."
                )
                else -> Quad(
                    "Academic concept revision and examination preparation video.",
                    listOf(
                        "Active Recall principle: Test yourself rather than re-reading",
                        "Spaced Repetition: Revisit after 24 hours and 7 days",
                        "Feynman Technique: Explain in simple plain terms"
                    ),
                    listOf(
                        QuizQuestion(
                            "Which study method yields the highest retention rate?",
                            listOf("Active Recall & Self-testing", "Passive video watching", "Highlighting text", "Re-reading notes"),
                            0,
                            "Cognitive science shows active testing produces stronger neural pathways."
                        )
                    ),
                    "Take 5-minute concise notes summarizing the video in your own words."
                )
            }
        } else {
            Quad(
                "Non-academic content. Identified as entertainment or social media distraction.",
                listOf(
                    "Attention degradation from unrelated content",
                    "Dopamine overload interrupting deep work flow"
                ),
                emptyList(),
                "Switch back immediately to your study playlist."
            )
        }

        return VideoAnalysisResult(
            title = title,
            subject = fast.subject,
            isStudyRelated = isStudy,
            isShortsOrDistraction = fast.isShorts,
            topic = fast.topic,
            confidenceScore = fast.confidence,
            summary = summary,
            keyFormulasAndConcepts = formulas,
            studyRetentionQuiz = quiz,
            studyTips = tip,
            timeWastedNotice = if (!isStudy) "⚠️ Not counted towards study goals! Logged as distraction time." else null
        )
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
