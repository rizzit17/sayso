package com.samsung.prism.teachable.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class SpeechToText(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val tag = "SpeechToText"

    private val _state = MutableStateFlow(VoiceInputState.IDLE)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val _recognizedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to initialize SpeechRecognizer: ${e.message}")
            }
        } else {
            Log.w(tag, "Speech recognition is not available on this device")
        }
    }

    private var hasRetriedFallbackLanguage = false

    fun startListening(fallbackToUsLocale: Boolean = false) {
        if (_state.value == VoiceInputState.LISTENING) return

        _errorMessage.value = null

        if (speechRecognizer == null) {
            initRecognizer()
        }

        if (speechRecognizer == null) {
            Log.w(tag, "SpeechRecognizer unavailable, falling back to IDLE")
            _errorMessage.value = "Speech recognition service is unavailable on this device. Please type your command below."
            _state.value = VoiceInputState.ERROR
            return
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (fallbackToUsLocale) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
            _state.value = VoiceInputState.LISTENING
            _partialText.value = ""
        } catch (e: Exception) {
            Log.e(tag, "Error starting speech recognition: ${e.message}")
            _errorMessage.value = "Failed to start speech recognition: ${e.localizedMessage ?: e.message}"
            _state.value = VoiceInputState.ERROR
        }
    }

    fun stopListening() {
        if (_state.value == VoiceInputState.LISTENING) {
            _state.value = VoiceInputState.PROCESSING
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Error stopping speech recognition: ${e.message}")
            }
        }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(tag, "Error cancelling speech recognition: ${e.message}")
        }
        _state.value = VoiceInputState.IDLE
        _partialText.value = ""
    }

    /**
     * Text fallback input mode for automated evaluation tests and devices without microphones.
     */
    fun simulateSpeech(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        _state.value = VoiceInputState.PROCESSING
        _partialText.value = trimmed
        scope.launch {
            _recognizedText.emit(trimmed)
            _state.value = VoiceInputState.IDLE
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(tag, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
        _state.value = VoiceInputState.IDLE
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceInputState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                _state.value = VoiceInputState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = VoiceInputState.PROCESSING
            }

            override fun onError(error: Int) {
                Log.w(tag, "Speech recognition error code: $error")

                // Auto-retry once with en-US if language pack error (12 or 13)
                if ((error == 12 || error == 13) && !hasRetriedFallbackLanguage) {
                    hasRetriedFallbackLanguage = true
                    Log.i(tag, "Retrying speech recognition with default en-US fallback locale...")
                    scope.launch {
                        _state.value = VoiceInputState.IDLE
                        startListening(fallbackToUsLocale = true)
                    }
                    return
                }

                hasRetriedFallbackLanguage = false
                val friendlyMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Speech client error. Speech service may not be available on this device"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking clearly or typing below"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy, please try again"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many speech requests. Try again"
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Speech server disconnected"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Speech language not supported. Try typing command below"
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language pack unavailable on device. Try typing command below or download language pack in Android settings"
                    else -> "Speech recognition error (code $error). Try typing command below"
                }
                _errorMessage.value = friendlyMsg
                _state.value = VoiceInputState.ERROR
            }

            override fun onResults(results: Bundle?) {
                _state.value = VoiceInputState.PROCESSING
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestResult = matches?.firstOrNull() ?: ""
                if (bestResult.isNotBlank()) {
                    _partialText.value = bestResult
                    scope.launch {
                        _recognizedText.emit(bestResult)
                        _state.value = VoiceInputState.IDLE
                    }
                } else {
                    _state.value = VoiceInputState.IDLE
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    _partialText.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
