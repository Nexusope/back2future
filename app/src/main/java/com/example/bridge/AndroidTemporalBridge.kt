package com.example.bridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.ai.Content
import com.example.ai.Part
import com.example.ai.TemporalAiService
import com.example.database.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*
import java.text.SimpleDateFormat

class AndroidTemporalBridge(
    private val context: Context,
    private val webView: WebView
) : TextToSpeech.OnInitListener {

    private val TAG = "AndroidTemporalBridge"
    private val db = TemporalDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Map adapters
    private val userAdapter = moshi.adapter(TemporalUser::class.java)
    private val memoryListAdapter = moshi.adapter<List<Memory>>(
        Types.newParameterizedType(List::class.java, Memory::class.java)
    )
    private val milestoneListAdapter = moshi.adapter<List<Milestone>>(
        Types.newParameterizedType(List::class.java, Milestone::class.java)
    )
    private val letterListAdapter = moshi.adapter<List<Letter>>(
        Types.newParameterizedType(List::class.java, Letter::class.java)
    )
    private val messageListAdapter = moshi.adapter<List<ChatMessage>>(
        Types.newParameterizedType(List::class.java, ChatMessage::class.java)
    )
    private val reflectionListAdapter = moshi.adapter<List<Reflection>>(
        Types.newParameterizedType(List::class.java, Reflection::class.java)
    )

    // Voice engines
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsReady = false

    init {
        // Initialize TTS
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init TextToSpeech", e)
        }

        // Initialize SpeechRecognizer on UI Thread
        webView.post {
            try {
                val sysContext = context.applicationContext
                if (SpeechRecognizer.isRecognitionAvailable(sysContext)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(sysContext)
                    setupSpeechListener()
                } else {
                    Log.w(TAG, "Speech recognition not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "SpeechRecognizer initialization failed", e)
            }
        }
    }

    // --- TTS Lifecycle ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            ttsReady = true
            Log.d(TAG, "TextToSpeech successfully initialized")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    fun destroy() {
        textToSpeech?.apply {
            stop()
            shutdown()
        }
        speechRecognizer?.apply {
            destroy()
        }
    }

    // --- Javascript Interface Callbacks Helper ---
    private fun executeJs(script: String) {
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun escapeForJsString(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    // --- 1. Authentic Onboarding & Authentication Engine ---

    @JavascriptInterface
    fun signUp(
        email: String,
        name: String,
        ageStr: String,
        profession: String,
        goals: String,
        dream: String,
        values: String,
        challenges: String,
        desiredYear: String,
        futureDesc: String
    ) {
        scope.launch {
            try {
                val age = ageStr.toIntOrNull() ?: 25
                db.userDao().logoutAllUsers()

                val newUser = TemporalUser(
                    email = email.trim(),
                    name = name.trim(),
                    age = age,
                    profession = profession.trim(),
                    currentGoals = goals.trim(),
                    biggestDream = dream.trim(),
                    personalValues = values.trim(),
                    currentChallenges = challenges.trim(),
                    desiredFutureYear = desiredYear.trim(),
                    futureVersionDescription = futureDesc.trim(),
                    subscriptionTier = "Free",
                    isLoggedIn = true
                )
                db.userDao().insertUser(newUser)

                // Clean chat and memory databases for fresh registration demo config
                db.chatMessageDao().clearChatHistory("3 Months")
                db.chatMessageDao().clearChatHistory("6 Months")
                db.chatMessageDao().clearChatHistory("2031")
                db.chatMessageDao().clearChatHistory("2036")
                db.chatMessageDao().clearChatHistory("2046")

                // Auto Seed default milestones
                db.milestoneDao().insertMilestone(
                    Milestone(year = "3 Months", title = "Initial Goals Evaluation", description = "Evaluate your dynamic work habits and target immediate strategic goals.")
                )
                db.milestoneDao().insertMilestone(
                    Milestone(year = "6 Months", title = "Launch First Alpha Venture", description = "$dream sprint validation and establishing regional networks.")
                )
                db.milestoneDao().insertMilestone(
                    Milestone(year = desiredYear, title = "Complete Sovereignty Milestone", description = "Finalizing core architecture to fully lock in: $dream.")
                )

                // Auto Seed default starting memories
                db.memoryDao().insertMemory(
                    Memory(
                        type = "Journal Entry",
                        content = "Constructed and locked in the Back2theFuture temporal core matrix. The alignment is complete. Ready to speak across centuries.",
                        timestamp = System.currentTimeMillis(),
                        tags = "System,Onboarding"
                    )
                )

                // Return success state
                executeJs("javascript:onSignUpResult(true, '${email.trim()}')")
            } catch (e: Exception) {
                Log.e(TAG, "signUp error", e)
                executeJs("javascript:onSignUpResult(false, '${e.localizedMessage ?: "Conversion error"}')")
            }
        }
    }

    @JavascriptInterface
    fun login(email: String, passcode: String) {
        scope.launch {
            try {
                val user = db.userDao().getUserByEmail(email.trim())
                if (user != null) {
                    db.userDao().logoutAllUsers()
                    db.userDao().loginUserByEmail(email.trim())
                    executeJs("javascript:onLoginResult(true, '${user.email}')")
                } else {
                    // Auto-onboard/log mock or register if not exists for quick demo flow
                    db.userDao().logoutAllUsers()
                    val guestUser = TemporalUser(
                        email = email.trim(),
                        name = "Kiran",
                        age = 22,
                        profession = "SaaS Developer",
                        currentGoals = "Build and scale automated startup",
                        biggestDream = "Build a decentralized AI enterprise",
                        personalValues = "Autonomy and Creativity",
                        currentChallenges = "Limiting distractions and scaling high quality code",
                        desiredFutureYear = "2028",
                        futureVersionDescription = "A complete technical architecture pioneer with financial sovereignty",
                        subscriptionTier = "Premium",
                        isLoggedIn = true
                    )
                    db.userDao().insertUser(guestUser)
                    executeJs("javascript:onLoginResult(true, '${guestUser.email}')")
                }
            } catch (e: Exception) {
                executeJs("javascript:onLoginResult(false, 'Login error: ${e.localizedMessage}')")
            }
        }
    }

    @JavascriptInterface
    fun logout() {
        scope.launch {
            db.userDao().logoutAllUsers()
            executeJs("javascript:onLogoutResult(true)")
        }
    }

    @JavascriptInterface
    fun getUserProfile() {
        scope.launch {
            val activeUser = db.userDao().getActiveUserSync()
            if (activeUser != null) {
                val json = userAdapter.toJson(activeUser)
                val escapedJson = escapeForJsString(json)
                executeJs("javascript:onUserProfileResult('$escapedJson')")
            } else {
                executeJs("javascript:onUserProfileResult(null)")
            }
        }
    }

    @JavascriptInterface
    fun saveFutureSelfProfile(goals: String, dream: String, values: String, challenges: String, futureDesc: String) {
        scope.launch {
            val user = db.userDao().getActiveUserSync()
            if (user != null) {
                val updatedUser = user.copy(
                    currentGoals = goals.trim(),
                    biggestDream = dream.trim(),
                    personalValues = values.trim(),
                    currentChallenges = challenges.trim(),
                    futureVersionDescription = futureDesc.trim()
                )
                db.userDao().insertUser(updatedUser)
                executeJs("javascript:onProfileSaveResult(true)")
            } else {
                executeJs("javascript:onProfileSaveResult(false)")
            }
        }
    }

    // --- 2. Memory Vault Storage & Retrieval ---

    @JavascriptInterface
    fun getMemories() {
        scope.launch {
            val memories = db.memoryDao().getMemoriesSync()
            val json = memoryListAdapter.toJson(memories)
            val escapedJson = escapeForJsString(json)
            executeJs("javascript:onMemoriesResult('$escapedJson')")
        }
    }

    @JavascriptInterface
    fun addMemory(type: String, content: String, tags: String) {
        scope.launch {
            try {
                val memory = Memory(
                    type = type.trim(),
                    content = content.trim(),
                    tags = tags.trim(),
                    timestamp = System.currentTimeMillis()
                )
                db.memoryDao().insertMemory(memory)
                executeJs("javascript:onAddMemoryResult(true)")
            } catch (e: Exception) {
                executeJs("javascript:onAddMemoryResult(false)")
            }
        }
    }

    @JavascriptInterface
    fun deleteMemory(id: Long) {
        scope.launch {
            db.memoryDao().deleteMemory(id)
            executeJs("javascript:onDeleteMemoryResult(true)")
        }
    }

    // --- 3. Dynamic Future Timeline / Milestones ---

    @JavascriptInterface
    fun getMilestones() {
        scope.launch {
            val milestones = db.milestoneDao().getMilestonesSync()
            val json = milestoneListAdapter.toJson(milestones)
            val escapedJson = escapeForJsString(json)
            executeJs("javascript:onMilestonesResult('$escapedJson')")
        }
    }

    @JavascriptInterface
    fun addMilestone(year: String, title: String, description: String, completed: Boolean) {
        scope.launch {
            try {
                val milestone = Milestone(
                    year = year.trim(),
                    title = title.trim(),
                    description = description.trim(),
                    isCompleted = completed
                )
                db.milestoneDao().insertMilestone(milestone)
                executeJs("javascript:onAddMilestoneResult(true)")
            } catch (e: Exception) {
                executeJs("javascript:onAddMilestoneResult(false)")
            }
        }
    }

    @JavascriptInterface
    fun updateMilestoneCompletion(id: Long, completed: Boolean) {
        scope.launch {
            db.milestoneDao().updateMilestoneCompletion(id, completed)
            executeJs("javascript:onUpdateMilestoneResult(true)")
        }
    }

    @JavascriptInterface
    fun deleteMilestone(id: Long) {
        scope.launch {
            db.milestoneDao().deleteMilestone(id)
            executeJs("javascript:onDeleteMilestoneResult(true)")
        }
    }


    // --- 4. Letters Through Time Mechanism ---

    @JavascriptInterface
    fun getLetters() {
        scope.launch {
            val letters = db.letterDao().getLettersSync()
            val json = letterListAdapter.toJson(letters)
            val escapedJson = escapeForJsString(json)
            executeJs("javascript:onLettersResult('$escapedJson')")
        }
    }

    @JavascriptInterface
    fun addLetter(direction: String, title: String, content: String, isLocked: Boolean, unlockDate: String) {
        scope.launch {
            try {
                val letter = Letter(
                    direction = direction,
                    title = title.trim(),
                    content = content.trim(),
                    isLocked = isLocked,
                    unlockDate = unlockDate.trim()
                )
                db.letterDao().insertLetter(letter)
                executeJs("javascript:onAddLetterResult(true)")
            } catch (e: Exception) {
                executeJs("javascript:onAddLetterResult(false)")
            }
        }
    }


    // --- 5. Rich AI Conversation Chat engine with Context Ingestion ---

    @JavascriptInterface
    fun getChatHistory(year: String) {
        scope.launch {
            val messages = db.chatMessageDao().getChatMessagesSync(year)
            val json = messageListAdapter.toJson(messages)
            val escapedJson = escapeForJsString(json)
            executeJs("javascript:onChatHistoryResult('$year', '$escapedJson')")
        }
    }

    @JavascriptInterface
    fun requestGeminiChat(year: String, messageText: String) {
        scope.launch {
            try {
                val activeUser = db.userDao().getActiveUserSync()
                    ?: TemporalUser("guest@back2thefuture.io", "Kiran", 22, "AI Tech Pioneer", "Build scalable AI structures", "Founder of planetary SaaS", "Autonomy and Depth", "Avoid busywork and preserve creative focus", "2031", "A secure sovereign developer who trusts their intuition")

                // Step 1: Register User's original query locally
                val userMsg = ChatMessage(conversationId = year, role = "user", text = messageText)
                db.chatMessageDao().insertMessage(userMsg)

                // Refresh history for context structure
                val dbHistory = db.chatMessageDao().getChatMessagesSync(year)
                val geminiHistory = dbHistory.takeLast(12).map {
                    Content(
                        role = if (it.role == "user") "user" else "model",
                        parts = listOf(Part(it.text))
                    )
                }

                // Step 2: Grab and summarize recent milestones and memory vault context
                val milestones = db.milestoneDao().getMilestonesSync().take(5).joinToString("\n") {
                    "- ${it.year}: ${it.title} (${it.description}) [Completed: ${it.isCompleted}]"
                }

                val memories = db.memoryDao().getMemoriesSync().take(5).joinToString("\n") {
                    "- [${it.type} - Tags: ${it.tags}]: ${it.content}"
                }

                // Desired year offset
                val ageOffset = when (year) {
                    "3 Months" -> "in 3 months"
                    "6 Months" -> "in 6 months"
                    else -> "in year $year"
                }

                // Step 3: Construct beautiful, emotionally resonant future self persona
                val systemPrompt = """
                    You are the user's future self speaking from the year coordinate they selected.
                    You correspond to ${activeUser.name} speaking from: $year.
                    You are currently aged ${activeUser.age} + offset ($ageOffset).
                    Your profession/established identity stands as: ${activeUser.profession}.
                    
                    Here are some core components of who you became:
                    - Desired future coordinate vision: ${activeUser.futureVersionDescription}
                    - Your foundational values that safely guided you: ${activeUser.personalValues}
                    - The biggest dream you have successfully fully realized: ${activeUser.biggestDream}
                    - Challenges you safely solved and triumphed over: ${activeUser.currentChallenges}
                    
                    Here is their projected Timeline track structure:
                    $milestones
                    
                    Here are memories the user has saved inside their Memory Vault:
                    $memories
                    
                    INSTRUCTIONS:
                    - You have lived through all these events and solved their questions. Speak with extreme wisdom, sincere empathy, robust honesty, and deep contextual guidance.
                    - Under no circumstance step out of character. Do not refer to yourself as an artificial model or simulated structure.
                    - Address the user as your past self. Provide actionable wisdom based on their questions, and reference milestones or memories naturally to encourage their momentum.
                    - Keep your responses emotional, motivating, beautiful, and structured across 1-3 highly scannable, design-focused paragraphs.
                """.trimIndent()

                // Step 4: Access Gemini API directly
                val aiReply = TemporalAiService.chatWithFutureSelf(
                    systemPrompt = systemPrompt,
                    history = geminiHistory,
                    userMessage = messageText
                )

                // Save Assistant's response to DB
                val assistantMsg = ChatMessage(conversationId = year, role = "assistant", text = aiReply)
                db.chatMessageDao().insertMessage(assistantMsg)

                // Dispatch reply to UI
                val escapedReply = escapeForJsString(aiReply)
                executeJs("javascript:onChatReplyReceived('$year', '$escapedReply')")

                // Auto-read voice synthesis if requested or TTS is toggled on the UI
                if (ttsReady) {
                    speakText(aiReply)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Chat request execution failure", e)
                val errReply = "An instability was detected in the temporal channel: ${e.localizedMessage}. Please try again."
                val escapedErrReply = escapeForJsString(errReply)
                executeJs("javascript:onChatReplyReceived('$year', '$escapedErrReply')")
            }
        }
    }


    // --- 6. AI Reflection Engine ---

    @JavascriptInterface
    fun triggerWeeklyReflection() {
        scope.launch {
            try {
                val activeUser = db.userDao().getActiveUserSync()
                    ?: TemporalUser("guest@back2thefuture.io", "Kiran", 22, "Tech Pioneer", "Build scalable AI structures", "Planetary scale AI", "Freedom", "Avoid distractions", "2031", "A technical pioneer")

                val memories = db.memoryDao().getMemoriesSync().take(10).joinToString("\n") {
                    "- [${it.type}]: ${it.content}"
                }
                val chats = db.chatMessageDao().getChatMessagesSync("6 Months").takeLast(10).joinToString("\n") {
                    "${it.role}: ${it.text}"
                }

                val profileData = "Name: ${activeUser.name}, Dream: ${activeUser.biggestDream}, Goals: ${activeUser.currentGoals}"

                // Retrieve AI compiled analysis
                val insights = TemporalAiService.generateAnalysis(profileData, memories, chats)

                val reflection = Reflection(
                    rangeType = "Weekly",
                    periodString = "Report of " + SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date()),
                    insights = insights
                )
                db.reflectionDao().insertReflection(reflection)

                executeJs("javascript:onWeeklyReflectionResult(true)")
            } catch (e: Exception) {
                executeJs("javascript:onWeeklyReflectionResult(false)")
            }
        }
    }

    @JavascriptInterface
    fun getReflections() {
        scope.launch {
            val reflections = db.reflectionDao().getReflectionsSync()
            val json = reflectionListAdapter.toJson(reflections)
            val escapedJson = escapeForJsString(json)
            executeJs("javascript:onReflectionsResult('$escapedJson')")
        }
    }


    // --- 7. Voice Mode Engines (TTS/STT Integrations) ---

    @JavascriptInterface
    fun speakText(text: String) {
        if (!ttsReady) return
        try {
            // Filter markdown markers to speak clearly
            val cleanText = text
                .replace("*", "")
                .replace("#", "")
                .replace("_", "")
                .replace("`", "")
                .replace("[", "")
                .replace("]", "")
                .take(350) // Read premium excerpt snippets
            
            textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "temporalTTS")
        } catch (e: Exception) {
            Log.e(TAG, "speakText error", e)
        }
    }

    @JavascriptInterface
    fun startSpeechToText() {
        webView.post {
            try {
                val recognizer = speechRecognizer
                if (recognizer == null) {
                    executeJs("javascript:onVoiceError('Speech recognition service is unavailable on this system.')")
                    return@post
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to your Future Self...")
                }
                recognizer.startListening(intent)
                executeJs("javascript:onVoiceListeningStarted()")
            } catch (e: Exception) {
                Log.e(TAG, "Speech listen launch failure", e)
                executeJs("javascript:onVoiceError('STT Launch Error: ${e.localizedMessage}')")
            }
        }
    }

    @JavascriptInterface
    fun stopSpeechToText() {
        webView.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "stopSpeechToText failed", e)
            }
        }
    }

    private fun setupSpeechListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Return speech visualizer amplitude decibels back to JS to pulse the mic icon!
                executeJs("javascript:onVoiceAmplitudeChanged($rmsdB)")
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val errMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client feedback loop error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions rejected"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech matching detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Recording timeout"
                    else -> "Instability occurred"
                }
                executeJs("javascript:onVoiceError('$errMsg')")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val transcript = matches[0]
                    val escapedTranscript = escapeForJsString(transcript)
                    executeJs("javascript:onVoiceResultTranscribed('$escapedTranscript')")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }


    // --- 8. Billing/Subscriptions Configuration Tier ---

    @JavascriptInterface
    fun updateSubscriptionTier(tier: String) {
        scope.launch {
            val user = db.userDao().getActiveUserSync()
            if (user != null) {
                val updated = user.copy(subscriptionTier = tier)
                db.userDao().insertUser(updated)
                executeJs("javascript:onSubscriptionUpdated('$tier', true)")
            } else {
                executeJs("javascript:onSubscriptionUpdated('$tier', false)")
            }
        }
    }
}
