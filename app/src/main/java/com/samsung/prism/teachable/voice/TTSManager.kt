package com.samsung.prism.teachable.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TTSManager(context: Context) {
    private val tag = "TTSManager"
    private var tts: TextToSpeech? = null
    private var isReady = false
    var isEnabled: Boolean = true

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var lastSpokenText: String? = null
    private var lastSpokenTime: Long = 0

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

                    // Configure proper AudioAttributes to prevent buffer underrun/crackling on emulators
                    try {
                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(attrs)
                        tts?.setSpeechRate(1.0f)
                        tts?.setPitch(1.0f)
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to set audio attributes: ${e.message}")
                    }

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            abandonFocus()
                        }
                        override fun onError(utteranceId: String?) {
                            abandonFocus()
                        }
                    })
                } else {
                    Log.w(tag, "TTS initialization failed: $status")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to initialize TextToSpeech: ${e.message}")
        }
    }

    private fun requestFocus() {
        try {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        } catch (e: Exception) {
            Log.w(tag, "AudioFocus request error: ${e.message}")
        }
    }

    private fun abandonFocus() {
        try {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        } catch (e: Exception) {
            Log.w(tag, "AudioFocus abandon error: ${e.message}")
        }
    }

    fun speak(text: String) {
        if (!isEnabled || text.isBlank()) return

        // Debounce identical speech calls within 400ms to avoid choppy cutting/cracking
        val now = System.currentTimeMillis()
        if (text == lastSpokenText && (now - lastSpokenTime) < 400) {
            return
        }
        lastSpokenText = text
        lastSpokenTime = now

        if (isReady && tts != null) {
            try {
                requestFocus()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prism_tts_${System.currentTimeMillis()}")
            } catch (e: Exception) {
                Log.w(tag, "TTS speak error: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            tts?.stop()
            abandonFocus()
        } catch (e: Exception) {
            Log.w(tag, "TTS stop error: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            abandonFocus()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "TTS shutdown error: ${e.message}")
        }
        tts = null
        isReady = false
    }
}
