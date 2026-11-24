package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.assistant.PendingPhoneAction
import com.example.assistant.PhoneActionManager
import com.example.assistant.VoiceAssistantManager
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.MemoryFact
import com.example.data.model.UserPreferences
import com.example.data.repository.InternetResearchService
import com.example.data.repository.JarvisFirestoreRepository
import com.example.data.repository.ResearchSource
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AssistantViewModel"

enum class JarvisModelOption(val modelId: String, val label: String) {
    FLASH("gemini-3.8-flash", "Flash 3.8"),
    ARGON("gemini-4-argon", "Argon 4 Pro"),
    LITE("gemini-3.5-flash-lite", "Flash Lite")
}

data class PendingAttachment(
    val label: String,
    val mimeType: String,
    val bitmap: Bitmap? = null,
    val bytes: ByteArray? = null
)

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data object Thinking : ChatStreamState
    data class Streaming(val partialText: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

class AssistantViewModel(
    private val repository: JarvisFirestoreRepository,
    private val researchService: InternetResearchService,
    private val currentUserId: String
) : ViewModel() {

    private val flashModel: GenerativeModel by lazy { Firebase.ai.generativeModel(JarvisModelOption.FLASH.modelId) }
    private val argonModel: GenerativeModel by lazy { Firebase.ai.generativeModel(JarvisModelOption.ARGON.modelId) }
    private val liteModel: GenerativeModel by lazy { Firebase.ai.generativeModel(JarvisModelOption.LITE.modelId) }

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations(currentUserId)
        .catch { err ->
            if (err is CancellationException) throw err
            Log.e(TAG, "Failed observing conversations", err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val memories: StateFlow<List<MemoryFact>> = repository.observeMemories(currentUserId)
        .catch { err ->
            if (err is CancellationException) throw err
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val preferences: StateFlow<UserPreferences> = repository.observePreferences(currentUserId)
        .catch { err ->
            if (err is CancellationException) throw err
            emit(UserPreferences())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    @Suppress("OPT_IN_USAGE")
    val messages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(currentUserId, convId)
        }
        .catch { err ->
            if (err is CancellationException) throw err
            Log.e(TAG, "Failed observing messages", err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedModel = MutableStateFlow(JarvisModelOption.FLASH)
    val selectedModel: StateFlow<JarvisModelOption> = _selectedModel.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    private val _streamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val streamState: StateFlow<ChatStreamState> = _streamState.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _pendingPhoneAction = MutableStateFlow<PendingPhoneAction?>(null)
    val pendingPhoneAction: StateFlow<PendingPhoneAction?> = _pendingPhoneAction.asStateFlow()

    private val _lastSources = MutableStateFlow<List<ResearchSource>>(emptyList())
    val lastSources: StateFlow<List<ResearchSource>> = _lastSources.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var activeGenerationJob: Job? = null

    fun selectModel(option: JarvisModelOption) {
        _selectedModel.value = option
    }

    fun toggleWebSearch() {
        _webSearchEnabled.value = !_webSearchEnabled.value
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        _lastSources.value = emptyList()
    }

    fun startNewConversation() {
        _activeConversationId.value = null
        _lastSources.value = emptyList()
        _streamState.value = ChatStreamState.Idle
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
        }
    }

    fun attachImageUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return@launch
                val original = input.use { BitmapFactory.decodeStream(it) } ?: return@launch
                val maxDim = 1600
                val scaled = if (original.width > maxDim || original.height > maxDim) {
                    val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * ratio).toInt().coerceAtLeast(1),
                        (original.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else original
                _pendingAttachment.value = PendingAttachment(
                    label = resolveFileName(context, uri) ?: "Selected Image",
                    mimeType = "image/jpeg",
                    bitmap = scaled
                )
            } catch (e: Exception) {
                _statusBanner.value = "Failed to load image: ${e.localizedMessage}"
            }
        }
    }

    fun attachDocumentUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@launch
                if (bytes.size > 18 * 1024 * 1024) {
                    _statusBanner.value = "File exceeds 18 MB limit for inline Gemini analysis."
                    return@launch
                }
                _pendingAttachment.value = PendingAttachment(
                    label = resolveFileName(context, uri) ?: "Attached Document",
                    mimeType = mimeType,
                    bytes = bytes
                )
            } catch (e: Exception) {
                _statusBanner.value = "Failed to read document: ${e.localizedMessage}"
            }
        }
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun queuePhoneAction(action: PendingPhoneAction) {
        _pendingPhoneAction.value = action
    }

    fun dismissPhoneAction() {
        _pendingPhoneAction.value = null
    }

    fun executePendingPhoneAction(context: Context) {
        val action = _pendingPhoneAction.value ?: return
        _pendingPhoneAction.value = null
        val result = PhoneActionManager.executeAction(context, action)
        _statusBanner.value = result.fold(
            onSuccess = { it },
            onFailure = { it.localizedMessage ?: "Unable to perform action." }
        )
    }

    fun saveMemoryFact(fact: String) {
        viewModelScope.launch {
            repository.addMemory(fact).fold(
                onSuccess = { _statusBanner.value = "Saved to Jarvis Memory." },
                onFailure = { _statusBanner.value = it.localizedMessage ?: "Failed to save memory." }
            )
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _streamState.value = ChatStreamState.Idle
    }

    fun fetchWeatherIntoChat(city: String, voiceManager: VoiceAssistantManager?, autoSpeak: Boolean) {
        viewModelScope.launch {
            _streamState.value = ChatStreamState.Thinking
            val report = researchService.fetchWeatherForCity(city)
            report.fold(
                onSuccess = { w ->
                    val text = "Live Weather for ${w.locationName}: ${w.temperatureC}°C (${w.conditionLabel}), Humidity ${w.humidityPercent}%, Wind ${w.windSpeedKmh} km/h."
                    persistTurn("What's the weather in $city?", text, voiceManager, autoSpeak)
                    _streamState.value = ChatStreamState.Idle
                },
                onFailure = { err ->
                    _streamState.value = ChatStreamState.Error(err.localizedMessage ?: "Weather lookup failed.")
                }
            )
        }
    }

    fun fetchWeatherByCoordsIntoChat(lat: Double, lon: Double, voiceManager: VoiceAssistantManager?, autoSpeak: Boolean) {
        viewModelScope.launch {
            _streamState.value = ChatStreamState.Thinking
            researchService.fetchWeatherForCoords(lat, lon).fold(
                onSuccess = { w ->
                    val text = "Live Local Weather: ${w.temperatureC}°C (${w.conditionLabel}), Humidity ${w.humidityPercent}%, Wind ${w.windSpeedKmh} km/h."
                    persistTurn("Check my local weather", text, voiceManager, autoSpeak)
                    _streamState.value = ChatStreamState.Idle
                },
                onFailure = { err ->
                    _streamState.value = ChatStreamState.Error(err.localizedMessage ?: "Local weather lookup failed.")
                }
            )
        }
    }

    fun sendMessage(
        rawPrompt: String,
        voiceManager: VoiceAssistantManager? = null,
        speakResponse: Boolean = false
    ) {
        val promptText = rawPrompt.trim()
        val attachment = _pendingAttachment.value
        if (promptText.isBlank() && attachment == null) return

        // Detect phone action intent and ask explicit confirmation first
        PhoneActionManager.detectActionFromPrompt(promptText)?.let { detected ->
            _pendingPhoneAction.value = detected
        }

        val effectivePrompt = promptText.ifBlank { "Please analyze the attached ${attachment?.label}." }
        _pendingAttachment.value = null
        _lastSources.value = emptyList()

        // Fast exact offline math shortcut if purely an expression
        val mathAnswer = PhoneActionManager.tryEvaluateSimpleMath(effectivePrompt)
        if (mathAnswer != null && attachment == null && !_webSearchEnabled.value) {
            val formatted = if (mathAnswer % 1.0 == 0.0) mathAnswer.toLong().toString() else "%.4f".format(mathAnswer).trimEnd('0').trimEnd('.')
            viewModelScope.launch {
                persistTurn(effectivePrompt, "Result: **$formatted**", voiceManager, speakResponse)
            }
            return
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _streamState.value = ChatStreamState.Thinking
            try {
                // Ensure conversation exists in Firestore
                val convId = ensureConversation(effectivePrompt)
                repository.appendMessage(
                    conversationId = convId,
                    role = "user",
                    text = effectivePrompt,
                    attachmentLabel = attachment?.label
                )

                // Optional real web research or URL fetch
                val webContextBuilder = StringBuilder()
                val urlMatch = Regex("https?://\\S+").find(effectivePrompt)?.value
                if (urlMatch != null) {
                    val pageText = researchService.fetchUrlReadableText(urlMatch).getOrNull()
                    if (!pageText.isNullOrBlank()) {
                        _lastSources.value = listOf(ResearchSource(title = urlMatch, snippet = pageText.take(160), url = urlMatch))
                        webContextBuilder.append("\n[Fetched Webpage Content from $urlMatch]:\n${pageText.take(6000)}\n")
                    }
                } else if (_webSearchEnabled.value) {
                    val sources = researchService.searchWeb(effectivePrompt).getOrDefault(emptyList())
                    _lastSources.value = sources
                    if (sources.isNotEmpty()) {
                        webContextBuilder.append("\n[Live Internet Research Sources]:\n")
                        sources.forEachIndexed { idx, src ->
                            webContextBuilder.append("${idx + 1}. ${src.title} (${src.url}): ${src.snippet}\n")
                        }
                    }
                }

                val systemContext = buildSystemPrompt(webContextBuilder.toString())
                val historyContents = buildConversationContents(messages.value)
                val userContent = buildUserContent(systemContext, effectivePrompt, attachment)

                val model = when (_selectedModel.value) {
                    JarvisModelOption.FLASH -> flashModel
                    JarvisModelOption.ARGON -> argonModel
                    JarvisModelOption.LITE -> liteModel
                }

                val accumulated = StringBuilder()
                withTimeout(60_000L) {
                    val chat = model.startChat(history = historyContents)
                    chat.sendMessageStream(userContent).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        accumulated.append(chunkText)
                        _streamState.value = ChatStreamState.Streaming(accumulated.toString())
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    val sourcesSuffix = if (_lastSources.value.isNotEmpty()) {
                        "\n\nSources:\n" + _lastSources.value.joinToString("\n") { "- ${it.title}: ${it.url}" }
                    } else ""
                    val fullReply = finalReply + sourcesSuffix
                    repository.appendMessage(
                        conversationId = convId,
                        role = "model",
                        text = fullReply
                    )
                    _streamState.value = ChatStreamState.Idle
                    if (speakResponse) {
                        voiceManager?.speak(finalReply)
                    }
                } else {
                    _streamState.value = ChatStreamState.Error("Jarvis received an empty response from Gemini.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini stream timed out", e)
                _streamState.value = ChatStreamState.Error("Request timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat error", e)
                _streamState.value = ChatStreamState.Error(e.localizedMessage ?: "Failed to generate response.")
            }
        }
    }

    private suspend fun persistTurn(
        userPrompt: String,
        modelReply: String,
        voiceManager: VoiceAssistantManager?,
        speak: Boolean
    ) {
        val convId = ensureConversation(userPrompt)
        repository.appendMessage(convId, "user", userPrompt)
        repository.appendMessage(convId, "model", modelReply)
        if (speak) voiceManager?.speak(modelReply)
    }

    private suspend fun ensureConversation(firstPrompt: String): String {
        val existing = _activeConversationId.value
        if (!existing.isNullOrBlank()) return existing
        val created = repository.createConversation(
            title = firstPrompt.take(48).ifBlank { "Jarvis Session" },
            modelName = _selectedModel.value.modelId
        ).getOrThrow()
        _activeConversationId.value = created
        return created
    }

    private fun buildSystemPrompt(webContext: String): String {
        val prefs = preferences.value
        val activeMemories = if (prefs.memoryEnabled) {
            memories.value.filter { it.active }.map { it.fact }
        } else emptyList()

        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.getDefault()).format(Date())
        return buildString {
            append("You are JARVIS AI, an intelligent, fast, friendly, and professional personal AI assistant. ")
            append("Fluently understand and reply in the exact language the user uses (English, Urdu, Roman Urdu, or Hindi). ")
            append("Current local device time: $nowStr. ")
            append("User preferred language setting: ${prefs.preferredLanguage}. ")
            append("When writing code (HTML/CSS/JS/Python/Kotlin), provide clean, complete, runnable code inside fenced markdown code blocks (e.g., ```html ... ```). ")
            append("Never pretend an external phone action, camera capture, or file modification ran unless confirmed. ")
            append("If sources are provided below, cite them accurately. ")
            if (activeMemories.isNotEmpty()) {
                append("\nRemembered User Preferences:\n")
                activeMemories.forEach { append("- $it\n") }
            }
            if (webContext.isNotBlank()) {
                append(webContext)
            }
        }
    }

    private fun buildConversationContents(history: List<ChatMessage>): List<Content> {
        return history.takeLast(14).map { msg ->
            content(role = if (msg.role == "model") "model" else "user") {
                text(msg.text)
            }
        }
    }

    private fun buildUserContent(
        systemContext: String,
        prompt: String,
        attachment: PendingAttachment?
    ): Content {
        return content(role = "user") {
            text("$systemContext\n\nUser request:\n$prompt")
            if (attachment?.bitmap != null) {
                image(attachment.bitmap)
            } else if (attachment?.bytes != null) {
                inlineData(attachment.bytes, attachment.mimeType)
            }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    companion object {
        fun provideFactory(
            repository: JarvisFirestoreRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AssistantViewModel(
                    repository = repository,
                    researchService = InternetResearchService(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
