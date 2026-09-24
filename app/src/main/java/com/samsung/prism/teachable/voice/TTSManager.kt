package com.samsung.prism.teachable.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager(context: Context) {
    private val tag = "TTSManager"
    private var tts: TextToSpeech? = null
    private var isReady = false
    var isEnabled: Boolean = true

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(tag, "TTS language not supported or missing data")
                    } else {
                        isReady = true
                    }
                } else {
                    Log.w(tag, "TTS initialization failed: $status")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to initialize TextToSpeech: ${e.message}")
        }
    }

    fun speak(text: String) {
        if (!isEnabled || text.isBlank()) return
        if (isReady && tts != null) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prism_tts_${System.currentTimeMillis()}")
            } catch (e: Exception) {
                Log.w(tag, "TTS speak error: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "TTS stop error: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "TTS shutdown error: ${e.message}")
        }
        tts = null
        isReady = false
    }
}
