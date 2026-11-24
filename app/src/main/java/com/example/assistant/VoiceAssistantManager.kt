package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceAssistantManager(context: Context) {
    private val appContext = context.applicationContext

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var onFinalResultCallback: ((String) -> Unit)? = null
    private var wakeWordModeActive = false

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            }
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(
        languageTag: String = "en-US",
        wakeWordMode: Boolean = false,
        onResult: (String) -> Unit
    ) {
        stopSpeaking()
        _voiceError.value = null
        wakeWordModeActive = wakeWordMode
        onFinalResultCallback = onResult

        if (!isSpeechRecognitionAvailable()) {
            _voiceError.value = "Speech recognition service is not available on this device."
            _isListening.value = false
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _partialTranscript.value = ""
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network speech error"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap mic to try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                        else -> "Speech recognition error ($error)"
                    }
                    _voiceError.value = message
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    _partialTranscript.value = spoken
                    if (spoken.isNotBlank()) {
                        if (wakeWordModeActive) {
                            val lower = spoken.lowercase()
                            if (lower.startsWith("hey jarvis") || lower.startsWith("jarvis")) {
                                val stripped = spoken
                                    .replaceFirst(Regex("(?i)^(hey\\s+)?jarvis[,:\\s]*"), "")
                                    .ifBlank { "Hello Jarvis" }
                                onFinalResultCallback?.invoke(stripped)
                            } else {
                                onFinalResultCallback?.invoke(spoken)
                            }
                        } else {
                            onFinalResultCallback?.invoke(spoken)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _voiceError.value = e.localizedMessage ?: "Failed to start microphone listener."
        }
    }

    fun stopListening() {
        _isListening.value = false
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Code block provided on screen.")
            .replace(Regex("[*_`#>]"), " ")
            .trim()
            .take(3500)
        if (cleanText.isBlank()) return

        val locale = detectLocaleForSpeech(cleanText)
        tts?.language = locale
        _isSpeaking.value = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        _isSpeaking.value = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun detectLocaleForSpeech(text: String): Locale {
        val hasUrduArabic = text.any { it in '\u0600'..'\u06FF' }
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        return when {
            hasUrduArabic -> Locale("ur", "PK")
            hasDevanagari -> Locale("hi", "IN")
            else -> Locale.US
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }
}
