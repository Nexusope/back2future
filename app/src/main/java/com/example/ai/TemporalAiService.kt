package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Moshi Mapped Request/Response Structs ---

data class Part(
    @Json(name = "text") val text: String
)

data class Content(
    @Json(name = "role") val role: String? = null,
    @Json(name = "parts") val parts: List<Part>
)

data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null
)

data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

data class Candidate(
    @Json(name = "content") val content: Content?
)

data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Singleton Service Client ---

object TemporalAiService {
    private const val TAG = "TemporalAiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Sends a unified conversation packet to the Gemini Model.
     */
    suspend fun chatWithFutureSelf(
        systemPrompt: String,
        history: List<Content>,
        userMessage: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is unconfigured or a placeholder!")
            return "Temporal connection disrupted. [System Info: Please add your GEMINI_API_KEY to the AI Studio Secrets panel to initialize the future quantum channel.]"
        }

        // Build total conversation contents
        val contentsList = mutableListOf<Content>()
        contentsList.addAll(history)
        contentsList.add(Content(role = "user", parts = listOf(Part(userMessage))))

        val systemInstructionContent = Content(
            parts = listOf(Part(systemPrompt))
        )

        val request = GenerateContentRequest(
            contents = contentsList,
            systemInstruction = systemInstructionContent,
            generationConfig = GenerationConfig(temperature = 0.7f)
        )

        return try {
            val response = apiService.generateContent(MODEL_NAME, apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No temporal transmission received. Try again."
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed", e)
            "Quantum synchronization failure: ${e.localizedMessage ?: "Network connection timeout"}"
        }
    }

    /**
     * Generates a structural reflection summary based on standard data profiles.
     */
    suspend fun generateAnalysis(
        onboardingProfile: String,
        memoryStream: String,
        chatExcerpt: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Unconfigured key. Can't produce reflection summary without a valid Gemini key."
        }

        val prompt = """
            You are the temporal analysis AI engine of Back2theFuture.
            Given the user's current onboarding profile, their saved memory stream, and dynamic chat context, generate a deeply motivational, analytical 'Reflection' report.
            The report should contain:
            1. WEEKLY PROGRESS INSIGHT (How did they move toward goals?)
            2. EXPERIENTIAL REALITY REFLECTION (What have they saved in the Memory Vault?)
            3. CRITICAL DECISION POINT (What hard choices wait ahead?)
            4. FUTURISTIC QUOTIENT (How aligned are they with their desired future?)
            
            Be inspiring, crisp, structured with clear subheadings, and speak with high design sophistication. Keep it under 250 words total.
            
            UserData:
            $onboardingProfile
             
            MemoryStream:
            $memoryStream
            
            ChatExcerpt:
            $chatExcerpt
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5f)
        )

        return try {
            val response = apiService.generateContent(MODEL_NAME, apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Analysis report compilation incomplete."
        } catch (e: Exception) {
            "Analysis compiling offline. (Please connect valid API key for authentic dynamic summaries)"
        }
    }
}
